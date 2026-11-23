package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.response.SuggestedSlotResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AppointmentSuggestionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired private AppointmentSuggestionService suggestionService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @MockitoBean
    private EmailService emailService;

    private LocalDate tomorrow;
    private User adminUser;
    private User patientUser;
    private User otherPatientUser;
    private Patient patient;
    private Doctor doctor1;          // Nội tổng quát
    private Doctor doctor2;          // Nội tổng quát
    private Doctor doctor3;          // Da liễu (khác chuyên khoa, không bao giờ được gợi ý)

    // key: "<doctorId>-<giờ>" → slot
    private final Map<String, TimeSlot> slots = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);
        tomorrow = LocalDate.now().plusDays(1);

        Specialty internal = specialtyRepository.save(
                Specialty.builder().name("Nội tổng quát - test").active(true).build());
        Specialty derma = specialtyRepository.save(
                Specialty.builder().name("Da liễu - test").active(true).build());

        doctor1 = createDoctor("doctor1", internal);
        doctor2 = createDoctor("doctor2", internal);
        doctor3 = createDoctor("doctor3", derma);

        for (Doctor d : List.of(doctor1, doctor2, doctor3)) {
            createSlots(d, "09:00", "09:30", "10:00", "11:00", "14:00");
        }

        adminUser = createUser("admin1", Role.ADMIN);
        patientUser = createUser("patient1", Role.PATIENT);
        patient = patientRepository.save(Patient.builder().user(patientUser).fullName("Bệnh nhân 1").build());
        otherPatientUser = createUser("patient2", Role.PATIENT);
        patientRepository.save(Patient.builder().user(otherPatientUser).fullName("Bệnh nhân 2").build());
    }

    // ===== Quy tắc slot và cờ sau khi hủy =====

    @Test
    void clinicCancel_shouldBlockSlot_andFlagAppointment() {
        Long id = book(doctor1, "09:00");

        cancel(id, adminUser, false);

        assertThat(slotStatus(doctor1, "09:00")).isEqualTo(SlotStatus.BLOCKED);
        assertThat(appointmentRepository.findById(id).orElseThrow().isCancelledByClinic()).isTrue();
    }

    @Test
    void patientCancel_shouldReopenSlot_andNotFlagAsClinic() {
        Long id = book(doctor1, "09:00");

        cancel(id, patientUser, false);

        assertThat(slotStatus(doctor1, "09:00")).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(appointmentRepository.findById(id).orElseThrow().isCancelledByClinic()).isFalse();
    }

    @Test
    void adminCancelOnBehalfOfPatient_shouldReopenSlot() {
        Long id = book(doctor1, "09:00");

        cancel(id, adminUser, true);

        assertThat(slotStatus(doctor1, "09:00")).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(appointmentRepository.findById(id).orElseThrow().isCancelledByClinic()).isFalse();
    }

    @Test
    void cancelByClinic_shouldBlockSlot_andFlagAppointment() {
        Long id = book(doctor1, "09:00");

        TestSecurityHelper.loginAs(adminUser);
        appointmentService.cancelByClinic(id, "Bác sĩ nghỉ đột xuất");

        assertThat(slotStatus(doctor1, "09:00")).isEqualTo(SlotStatus.BLOCKED);
        assertThat(appointmentRepository.findById(id).orElseThrow().isCancelledByClinic()).isTrue();
    }

    // ===== Gợi ý lịch tương tự =====

    @Test
    void suggest_afterClinicCancel_shouldRankSameDoctorFirst() {
        Long id = book(doctor1, "09:00");
        cancel(id, adminUser, false);

        TestSecurityHelper.loginAs(patientUser);
        List<SuggestedSlotResponse> result = suggestionService.suggestAlternatives(id);

        assertThat(result).hasSize(5);
        // Bác sĩ 1 còn 4 slot điểm 95, 85, 75, 65; sau đó bác sĩ 2 lúc 09:00 (45)
        assertThat(result).extracting(SuggestedSlotResponse::getDoctorId)
                .containsExactly(doctor1.getId(), doctor1.getId(), doctor1.getId(), doctor1.getId(), doctor2.getId());
        assertThat(result).extracting(SuggestedSlotResponse::getStartTime)
                .containsExactly(LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(11, 0),
                        LocalTime.of(14, 0), LocalTime.of(9, 0));

        assertThat(result.get(0).getReasons()).contains("Cùng bác sĩ", "Cùng khung giờ");
        // Không gợi ý slot đã khóa, không gợi ý khác chuyên khoa
        assertThat(result).extracting(SuggestedSlotResponse::getTimeSlotId)
                .doesNotContain(slot(doctor1, "09:00").getId());
        assertThat(result).extracting(SuggestedSlotResponse::getDoctorId)
                .doesNotContain(doctor3.getId());
    }

    @Test
    void suggest_shouldSkipSlotsViolatingBookingRulesOfThatPatient() {
        // Bệnh nhân đã có lịch khác lúc 11:00 với bác sĩ 2
        book(doctor2, "11:00");
        Long id = book(doctor1, "09:00");
        cancel(id, adminUser, false);

        TestSecurityHelper.loginAs(patientUser);
        List<SuggestedSlotResponse> result = suggestionService.suggestAlternatives(id);

        // Slot 11:00 của bác sĩ 1 điểm cao (75) nhưng trùng giờ lịch đã có → phải bị loại
        assertThat(result).hasSize(5);
        assertThat(result).extracting(SuggestedSlotResponse::getTimeSlotId)
                .doesNotContain(slot(doctor1, "11:00").getId());
    }

    @Test
    void suggest_afterPatientCancel_shouldThrow() {
        Long id = book(doctor1, "09:00");
        cancel(id, patientUser, false);

        TestSecurityHelper.loginAs(patientUser);
        assertThatThrownBy(() -> suggestionService.suggestAlternatives(id))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suggest_onActiveAppointment_shouldThrow() {
        Long id = book(doctor1, "09:00");

        assertThatThrownBy(() -> suggestionService.suggestAlternatives(id))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suggest_byAnotherPatient_shouldBeDenied() {
        Long id = book(doctor1, "09:00");
        cancel(id, adminUser, false);

        TestSecurityHelper.loginAs(otherPatientUser);

        assertThatThrownBy(() -> suggestionService.suggestAlternatives(id))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== helper =====

    private Long book(Doctor doctor, String time) {
        TestSecurityHelper.loginAs(patientUser);
        return appointmentService.book(AppointmentRequest.builder()
                .patientId(patient.getId())
                .timeSlotId(slot(doctor, time).getId())
                .build()).getId();
    }

    private void cancel(Long appointmentId, User actor, boolean onBehalfOfPatient) {
        TestSecurityHelper.loginAs(actor);
        CancelRequest req = new CancelRequest();
        req.setReason("Ly do test");
        req.setOnBehalfOfPatient(onBehalfOfPatient);
        appointmentService.cancel(appointmentId, req);
    }

    private TimeSlot slot(Doctor doctor, String time) {
        return slots.get(doctor.getId() + "-" + time);
    }

    private SlotStatus slotStatus(Doctor doctor, String time) {
        return timeSlotRepository.findById(slot(doctor, time).getId()).orElseThrow().getStatus();
    }

    private User createUser(String name, Role role) {
        return userRepository.save(User.builder()
                .username(name).email(name + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private Doctor createDoctor(String name, Specialty specialty) {
        User user = createUser(name, Role.DOCTOR);
        return doctorRepository.save(Doctor.builder()
                .user(user).specialty(specialty).fullName("BS " + name).active(true).build());
    }

    private void createSlots(Doctor doctor, String... times) {
        DoctorSchedule schedule = scheduleRepository.save(DoctorSchedule.builder()
                .doctor(doctor).workDate(tomorrow)
                .startTime(LocalTime.of(8, 0)).endTime(LocalTime.of(17, 0))
                .slotDurationMinutes(30).shiftType(ShiftType.CUSTOM).active(true).build());

        for (String time : times) {
            LocalTime start = LocalTime.parse(time);
            TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                    .schedule(schedule).doctor(doctor).slotDate(tomorrow)
                    .startTime(start).endTime(start.plusMinutes(30))
                    .status(SlotStatus.AVAILABLE).build());
            slots.put(doctor.getId() + "-" + time, slot);
        }
    }

}
