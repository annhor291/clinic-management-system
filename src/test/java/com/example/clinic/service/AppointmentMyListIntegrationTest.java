package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.payment.gateway.VnPayApiClient;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Kiểm tra GET /appointments/me: mỗi vai trò chỉ thấy lịch của chính mình
public class AppointmentMyListIntegrationTest extends BaseIntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired
    private PaymentMethodRepository paymentMethodRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;

    @MockitoBean private EmailService emailService;
    @MockitoBean
    private VnPayApiClient vnPayApiClient;

    private final LocalDate tomorrow = LocalDate.now().plusDays(1);

    private User adminUser, patientUser, patientUser2, doctorUser, doctorUser2, noProfileUser;
    private Doctor doctor, doctor2;
    private Patient patient, patient2;
    private List<TimeSlot> slots, slots2;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);
        paymentMethodRepository.save(PaymentMethod.builder().name("VNPay").code("VNPAY").active(true).build());

        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Me test").active(true).build());

        adminUser = user("admin_me", Role.ADMIN);
        patientUser = user("patient_me", Role.PATIENT);
        patientUser2 = user("patient2_me", Role.PATIENT);
        noProfileUser = user("noprofile_me", Role.PATIENT);
        doctorUser = user("doctor_me", Role.DOCTOR);
        doctorUser2 = user("doctor2_me", Role.DOCTOR);

        doctor = doctorRepository.save(Doctor.builder().user(doctorUser).specialty(specialty)
                .fullName("BS Mot").consultationFee(new BigDecimal("200000")).active(true).build());
        doctor2 = doctorRepository.save(Doctor.builder().user(doctorUser2).specialty(specialty)
                .fullName("BS Hai").consultationFee(new BigDecimal("200000")).active(true).build());
        patient = patientRepository.save(Patient.builder().user(patientUser).fullName("Benh nhan Mot").build());
        patient2 = patientRepository.save(Patient.builder().user(patientUser2).fullName("Benh nhan Hai").build());

        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), tomorrow, LocalTime.of(8, 0), LocalTime.of(10, 0),
                ShiftType.MORNING);
        doctorScheduleService.createFromApprovedRegistration(
                doctor2.getId(), tomorrow, LocalTime.of(10, 0), LocalTime.of(11, 0),
                ShiftType.AFTERNOON);

        slots = available(doctor);
        slots2 = available(doctor2);
    }

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private List<TimeSlot> available(Doctor d) {
        return timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                d.getId(), tomorrow, SlotStatus.AVAILABLE);
    }

    private void bookAs(User u, Patient p, TimeSlot slot) {
        TestSecurityHelper.loginAs(u);
        appointmentService.book(AppointmentRequest.builder()
                .patientId(p.getId()).timeSlotId(slot.getId()).build());
    }

    @Test
    void patient_me_returnsOnlyOwnAppointments() {
        bookAs(patientUser, patient, slots.get(0));
        bookAs(patientUser, patient, slots.get(2));
        bookAs(patientUser2, patient2, slots2.get(0));

        TestSecurityHelper.loginAs(patientUser);
        PageResponse<AppointmentResponse> result = appointmentService.getMyAppointments(0, 10);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent()).allMatch(a -> a.getPatientId().equals(patient.getId()));
    }

    @Test
    void patient_me_withoutProfile_returnsEmptyPage() {
        TestSecurityHelper.loginAs(noProfileUser);

        PageResponse<AppointmentResponse> result = appointmentService.getMyAppointments(0, 10);

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void patient_me_includesManagedRelatives_andPerPersonListsStaySeparate() {
        Patient relative = patientRepository.save(Patient.builder()
                .managedBy(patientUser).fullName("Nguoi than B").build());

        bookAs(patientUser, patient, slots.get(0));      // đặt cho chính mình
        bookAs(patientUser, relative, slots.get(2));     // đặt cho người thân
        bookAs(patientUser2, patient2, slots2.get(0));   // người khác, không được lẫn vào

        TestSecurityHelper.loginAs(patientUser);

        // Danh sách tổng: bản thân + người thân
        PageResponse<AppointmentResponse> all = appointmentService.getMyAppointments(0, 10);
        assertThat(all.getTotalElements()).isEqualTo(2);
        assertThat(all.getContent()).extracting(AppointmentResponse::getPatientId)
                .containsExactlyInAnyOrder(patient.getId(), relative.getId());

        // Danh sách riêng từng người
        assertThat(appointmentService.getByPatient(relative.getId(), 0, 10).getTotalElements()).isEqualTo(1);
        assertThat(appointmentService.getByPatient(patient.getId(), 0, 10).getTotalElements()).isEqualTo(1);

        // Hồ sơ của người không liên quan thì bị chặn
        assertThatThrownBy(() -> appointmentService.getByPatient(patient2.getId(), 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctor_me_returnsOnlyOwnAppointments() {
        bookAs(patientUser, patient, slots.get(0));
        bookAs(patientUser2, patient2, slots2.get(0));

        TestSecurityHelper.loginAs(doctorUser);
        PageResponse<AppointmentResponse> result = appointmentService.getMyAppointments(0, 10);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent()).allMatch(a -> a.getDoctorId().equals(doctor.getId()));
    }

    @Test
    void admin_me_isDenied() {
        TestSecurityHelper.loginAs(adminUser);

        assertThatThrownBy(() -> appointmentService.getMyAppointments(0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void booking_bufferRuleAppliesPerPatientProfile_notPerAccount() {
        Patient relative = patientRepository.save(Patient.builder()
                .managedBy(patientUser).fullName("Nguoi than B").build());

        bookAs(patientUser, patient, slots.get(2));    // A: 09:00-09:30
        bookAs(patientUser, relative, slots.get(1));   // B: 08:30-09:00, liền kề lịch của A nhưng khác hồ sơ → được

        // Chính A đặt thêm 09:30-10:00, liền kề lịch 09:00-09:30 của chính A → bị chặn
        assertThatThrownBy(() -> bookAs(patientUser, patient, slots.get(3)))
                .isInstanceOf(com.example.clinic.booking.engine.BookingRuleViolationException.class);
    }
}
