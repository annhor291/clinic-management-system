package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.*;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.*;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class WorkScheduleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DoctorWeeklyRegistrationService registrationService;
    @Autowired private DoctorLeaveRequestService leaveService;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;

    @MockitoBean
    private EmailService emailService;

    private User adminUser, doctorUser, patientUser;
    private Doctor doctor;
    private Patient patient;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);

        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("WorkSchedule test").active(true).build());
        adminUser = user("admin_ws", Role.ADMIN);
        doctorUser = user("doctor_ws", Role.DOCTOR);
        patientUser = user("patient_ws", Role.PATIENT);
        doctor = doctorRepository.save(Doctor.builder().user(doctorUser).specialty(specialty)
                .fullName("BS WorkSchedule").consultationFee(new BigDecimal("200000")).active(true).build());
        patient = patientRepository.save(Patient.builder().user(patientUser).fullName("Benh nhan WS").build());
    }

    // ===== Đăng ký lịch tuần =====

    @Test
    void approveRegistration_skipsDaysWithApprovedLeave() {
        LocalDate monday = nextMonday();
        LocalDate tuesday = monday.plusDays(1);

        // Thứ Ba tuần sau đã có nghỉ được duyệt từ trước
        TestSecurityHelper.loginAs(doctorUser);
        Long leaveId = leaveService.submit(leave(tuesday, tuesday)).getId();
        TestSecurityHelper.loginAs(adminUser);
        leaveService.approve(leaveId);

        TestSecurityHelper.loginAs(doctorUser);
        var submitted = registrationService.submit(weekRequest(monday, ShiftType.MORNING));
        assertThat(submitted.getWarnings()).hasSize(1);          // cảnh báo mềm khi nộp, không chặn

        TestSecurityHelper.loginAs(adminUser);
        registrationService.approve(submitted.getId());

        assertThat(scheduleDates(monday))
                .containsExactlyInAnyOrder(monday, monday.plusDays(2), monday.plusDays(3), monday.plusDays(4))
                .doesNotContain(tuesday);
    }

    @Test
    void approveRegistration_withoutLeave_generatesAllDays_andFullDayMakesTwoShifts() {
        LocalDate monday = nextMonday();

        TestSecurityHelper.loginAs(doctorUser);
        var submitted = registrationService.submit(weekRequest(monday, ShiftType.FULL));
        TestSecurityHelper.loginAs(adminUser);
        registrationService.approve(submitted.getId());

        LocalDate friday = monday.plusDays(4);
        // T2-T5 mỗi ngày 1 ca sáng, T6 là FULL nên tách 2 ca
        assertThat(scheduleDates(monday)).containsExactlyInAnyOrder(
                monday, monday.plusDays(1), monday.plusDays(2), monday.plusDays(3), friday, friday);
    }

    // ===== Đơn xin nghỉ =====

    @Test
    void rejectLeave_marksRejected_andKeepsAppointmentsUntouched() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), tomorrow, LocalTime.of(8, 0), LocalTime.of(10, 0), ShiftType.MORNING);
        TimeSlot slot = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), tomorrow, SlotStatus.AVAILABLE).get(0);
        TestSecurityHelper.loginAs(patientUser);
        Long appointmentId = appointmentService.book(AppointmentRequest.builder()
                .patientId(patient.getId()).timeSlotId(slot.getId()).build()).getId();

        TestSecurityHelper.loginAs(doctorUser);
        Long leaveId = leaveService.submit(leave(tomorrow, tomorrow)).getId();
        TestSecurityHelper.loginAs(adminUser);
        var rejected = leaveService.reject(leaveId,
                LeaveRejectRequest.builder().reason("Thieu bac si truc").build());

        assertThat(rejected.getStatus()).isEqualTo(LeaveStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("Thieu bac si truc");
        // Từ chối nghỉ thì lịch hẹn và slot giữ nguyên
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.PENDING);
        assertThat(timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void submitLeave_overlappingActiveRequest_isRejected() {
        LocalDate start = LocalDate.now().plusDays(3);
        TestSecurityHelper.loginAs(doctorUser);
        leaveService.submit(leave(start, start.plusDays(2)));

        assertThatThrownBy(() -> leaveService.submit(leave(start.plusDays(2), start.plusDays(3))))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void submitLeave_afterPreviousWasRejected_isAllowed() {
        LocalDate start = LocalDate.now().plusDays(3);
        TestSecurityHelper.loginAs(doctorUser);
        Long firstId = leaveService.submit(leave(start, start.plusDays(1))).getId();
        TestSecurityHelper.loginAs(adminUser);
        leaveService.reject(firstId, LeaveRejectRequest.builder().reason("Khong duyet").build());

        TestSecurityHelper.loginAs(doctorUser);
        var again = leaveService.submit(leave(start, start.plusDays(1)));

        assertThat(again.getStatus()).isEqualTo(LeaveStatus.PENDING);
    }

    @Test
    void submitLeave_endBeforeStart_isRejected() {
        LocalDate start = LocalDate.now().plusDays(5);
        TestSecurityHelper.loginAs(doctorUser);

        assertThatThrownBy(() -> leaveService.submit(leave(start, start.minusDays(1))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveLeave_twice_isRejected() {
        LocalDate day = LocalDate.now().plusDays(4);
        TestSecurityHelper.loginAs(doctorUser);
        Long leaveId = leaveService.submit(leave(day, day)).getId();
        TestSecurityHelper.loginAs(adminUser);
        leaveService.approve(leaveId);

        assertThatThrownBy(() -> leaveService.approve(leaveId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void approveLeave_byDoctor_isDenied() {
        LocalDate day = LocalDate.now().plusDays(4);
        TestSecurityHelper.loginAs(doctorUser);
        Long leaveId = leaveService.submit(leave(day, day)).getId();

        assertThatThrownBy(() -> leaveService.approve(leaveId))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== helper =====

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private LocalDate nextMonday() {
        return LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private LeaveRequestSubmitRequest leave(LocalDate start, LocalDate end) {
        return LeaveRequestSubmitRequest.builder()
                .type(LeaveType.PLANNED).startDate(start).endDate(end).reason("Viec rieng").build();
    }

    // T2-T5 ca sáng, T6 theo tham số
    private WeeklyRegistrationRequest weekRequest(LocalDate monday, ShiftType fridayShift) {
        List<DayShiftRequest> days = new ArrayList<>();
        for (DayOfWeek d : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)) {
            days.add(DayShiftRequest.builder().dayOfWeek(d).shiftType(ShiftType.MORNING).build());
        }
        days.add(DayShiftRequest.builder().dayOfWeek(DayOfWeek.FRIDAY).shiftType(fridayShift).build());
        return WeeklyRegistrationRequest.builder().weekStartDate(monday).days(days).build();
    }

    private List<LocalDate> scheduleDates(LocalDate monday) {
        return scheduleRepository.findByDoctorIdAndDateRange(doctor.getId(), monday, monday.plusDays(6))
                .stream().map(DoctorSchedule::getWorkDate).toList();
    }
}
