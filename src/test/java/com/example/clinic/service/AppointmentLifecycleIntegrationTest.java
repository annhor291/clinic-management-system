package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.*;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.*;
import com.example.clinic.payment.gateway.VnPayApiClient;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class AppointmentLifecycleIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired private DoctorService doctorService;
    @Autowired private DoctorLeaveRequestService leaveService;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean
    private EmailService emailService;
    @MockitoBean private VnPayApiClient vnPayApiClient;

    private final LocalDate tomorrow = LocalDate.now().plusDays(1);
    private final LocalDate dayAfter = LocalDate.now().plusDays(2);

    private User adminUser, patientUser, doctorUser;
    private Doctor doctor, otherDoctor;
    private Patient patient;
    private List<TimeSlot> slots;        // bác sĩ 1, ngày mai: 08:00, 08:30, 09:00, 09:30
    private List<TimeSlot> otherSlots;   // bác sĩ 2 (phí 300k), ngày mai: 10:00, 10:30

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);
        paymentMethodRepository.save(PaymentMethod.builder().name("VNPay").code("VNPAY").active(true).build());

        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Lifecycle test").active(true).build());

        adminUser = user("admin_lc", Role.ADMIN);
        patientUser = user("patient_lc", Role.PATIENT);
        doctorUser = user("doctor_lc", Role.DOCTOR);
        doctor = doctorRepository.save(Doctor.builder().user(doctorUser).specialty(specialty)
                .fullName("BS Mot").consultationFee(new BigDecimal("200000")).active(true).build());
        otherDoctor = doctorRepository.save(Doctor.builder().user(user("doctor2_lc", Role.DOCTOR))
                .specialty(specialty).fullName("BS Hai")
                .consultationFee(new BigDecimal("300000")).active(true).build());
        patient = patientRepository.save(Patient.builder().user(patientUser).fullName("Benh nhan LC").build());

        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), tomorrow, LocalTime.of(8, 0), LocalTime.of(10, 0), ShiftType.MORNING);
        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), dayAfter, LocalTime.of(8, 0), LocalTime.of(9, 0), ShiftType.MORNING);
        doctorScheduleService.createFromApprovedRegistration(
                otherDoctor.getId(), tomorrow, LocalTime.of(10, 0), LocalTime.of(11, 0), ShiftType.AFTERNOON);

        slots = available(doctor, tomorrow);
        otherSlots = available(otherDoctor, tomorrow);
    }

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private List<TimeSlot> available(Doctor d, LocalDate date) {
        return timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                d.getId(), date, SlotStatus.AVAILABLE);
    }

    private Long book(TimeSlot slot) {
        TestSecurityHelper.loginAs(patientUser);
        return appointmentService.book(AppointmentRequest.builder()
                .patientId(patient.getId()).timeSlotId(slot.getId()).build()).getId();
    }

    private SlotStatus status(TimeSlot slot) {
        return timeSlotRepository.findById(slot.getId()).orElseThrow().getStatus();
    }

    private Appointment appt(Long id) {
        return appointmentRepository.findById(id).orElseThrow();
    }

    private void savePayment(Long appointmentId, PaymentStatus status, String amount) {
        paymentRepository.save(Payment.builder()
                .appointment(appt(appointmentId))
                .paymentMethod(paymentMethodRepository.findByCodeAndActiveTrue("VNPAY").orElseThrow())
                .transactionCode("PAYLC" + status + appointmentId)
                .amount(new BigDecimal(amount)).status(status)
                .expiredAt(java.time.LocalDateTime.now().plusMinutes(15)).build());
    }

    // ===== Hủy lịch: slot theo người hủy =====

    @Test
    void cancel_byPatient_reopensSlot() {
        Long id = book(slots.get(0));
        appointmentService.cancel(id, CancelRequest.builder().reason("ban").build());

        assertThat(status(slots.get(0))).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(appt(id).isCancelledByClinic()).isFalse();
    }

    @Test
    void cancel_byAdmin_blocksSlot_andFlagsClinic() {
        Long id = book(slots.get(0));
        TestSecurityHelper.loginAs(adminUser);
        appointmentService.cancel(id, CancelRequest.builder().reason("bac si ban").build());

        assertThat(status(slots.get(0))).isEqualTo(SlotStatus.BLOCKED);
        assertThat(appt(id).isCancelledByClinic()).isTrue();
    }

    @Test
    void cancel_byAdminOnBehalfOfPatient_reopensSlot() {
        Long id = book(slots.get(0));
        TestSecurityHelper.loginAs(adminUser);
        appointmentService.cancel(id, CancelRequest.builder()
                .reason("benh nhan goi dien nho huy").onBehalfOfPatient(true).build());

        assertThat(status(slots.get(0))).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(appt(id).isCancelledByClinic()).isFalse();
    }

    // ===== Chặn bác sĩ =====

    @Test
    void leaveApproval_cancelsAffectedAppointments_blocksSlots_keepsOtherDays() {
        Long inLeave = book(slots.get(0));
        Long outsideLeave = book(available(doctor, dayAfter).get(0));

        TestSecurityHelper.loginAs(doctorUser);
        Long leaveId = leaveService.submit(LeaveRequestSubmitRequest.builder()
                .type(LeaveType.PLANNED).startDate(tomorrow).endDate(tomorrow).reason("Viec rieng").build()).getId();
        TestSecurityHelper.loginAs(adminUser);
        leaveService.approve(leaveId);

        assertThat(appt(inLeave).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appt(inLeave).isCancelledByClinic()).isTrue();
        assertThat(status(slots.get(0))).isEqualTo(SlotStatus.BLOCKED);
        assertThat(available(doctor, tomorrow)).isEmpty();
        assertThat(scheduleRepository.findByDoctorIdAndDateRange(doctor.getId(), tomorrow, tomorrow))
                .allMatch(s -> !s.isActive());

        assertThat(appt(outsideLeave).getStatus()).isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void deactivateDoctor_cancelsAllUpcoming_andBlocksEverything() {
        Long a = book(slots.get(0));
        Long b = book(available(doctor, dayAfter).get(0));

        TestSecurityHelper.loginAs(adminUser);
        doctorService.deactivate(doctor.getId());

        assertThat(appt(a).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appt(b).getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(available(doctor, tomorrow)).isEmpty();
        assertThat(available(doctor, dayAfter)).isEmpty();
        assertThat(doctorRepository.findById(doctor.getId()).orElseThrow().isActive()).isFalse();
    }

    // ===== Đổi lịch trực tiếp =====

    @Test
    void reschedule_unpaid_movesSameAppointment() {
        Long id = book(slots.get(0));
        String code = appt(id).getBookingCode();

        var res = appointmentService.reschedule(id,
                RescheduleRequest.builder().newTimeSlotId(slots.get(2).getId()).reason("ban viec").build());

        assertThat(res.getId()).isEqualTo(id);
        assertThat(res.getBookingCode()).isEqualTo(code);
        assertThat(appt(id).getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(appt(id).getTimeSlot().getId()).isEqualTo(slots.get(2).getId());
        assertThat(appt(id).getAppointmentTime()).isEqualTo(tomorrow.atTime(9, 0));
        assertThat(status(slots.get(0))).isEqualTo(SlotStatus.AVAILABLE);
        assertThat(status(slots.get(2))).isEqualTo(SlotStatus.BOOKED);
        assertThat(appointmentRepository.findAll()).hasSize(1);
    }

    @Test
    void reschedule_paidWithSameFee_keepsStatusAndPayment() {
        Long id = book(slots.get(0));
        TestSecurityHelper.loginAs(adminUser);
        appointmentService.confirm(id);
        savePayment(id, PaymentStatus.SUCCESS, "200000");

        TestSecurityHelper.loginAs(patientUser);
        appointmentService.reschedule(id,
                RescheduleRequest.builder().newTimeSlotId(slots.get(1).getId()).reason("doi gio").build());

        assertThat(appt(id).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(paymentRepository.findFirstByAppointmentIdAndStatus(id, PaymentStatus.SUCCESS)).isPresent();
    }

    @Test
    void reschedule_paidWithDifferentFee_isRejected_andNothingChanges() {
        Long id = book(slots.get(0));
        savePayment(id, PaymentStatus.SUCCESS, "200000");

        assertThatThrownBy(() -> appointmentService.reschedule(id,
                RescheduleRequest.builder().newTimeSlotId(otherSlots.get(0).getId()).reason("doi bs").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hủy");

        assertThat(appt(id).getTimeSlot().getId()).isEqualTo(slots.get(0).getId());
        assertThat(status(slots.get(0))).isEqualTo(SlotStatus.BOOKED);
        assertThat(status(otherSlots.get(0))).isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void reschedule_unpaidToDifferentFeeDoctor_cancelsOpenPaymentSession() {
        Long id = book(slots.get(0));
        savePayment(id, PaymentStatus.PENDING, "200000");

        appointmentService.reschedule(id,
                RescheduleRequest.builder().newTimeSlotId(otherSlots.get(0).getId()).reason("doi bs").build());

        assertThat(appt(id).getDoctor().getId()).isEqualTo(otherDoctor.getId());
        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(paymentRepository.findAll())
                        .allMatch(p -> p.getStatus() == PaymentStatus.CANCELLED));
    }

    @Test
    void doctor_cannotUpdateOtherDoctorProfile() {
        TestSecurityHelper.loginAs(doctorUser);

        DoctorUpdateRequest request = new DoctorUpdateRequest();
        request.setConsultationFee(new BigDecimal("999000"));

        assertThatThrownBy(() -> doctorService.update(otherDoctor.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctor_cannotChangeOwnFee() {
        TestSecurityHelper.loginAs(doctorUser);

        DoctorUpdateRequest request = new DoctorUpdateRequest();
        request.setConsultationFee(new BigDecimal("999000"));

        assertThatThrownBy(() -> doctorService.update(doctor.getId(), request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deactivateSchedule_withBookedSlot_isRejected() {
        book(slots.get(0));
        Long scheduleId = scheduleRepository
                .findByDoctorIdAndDateRange(doctor.getId(), tomorrow, tomorrow)
                .get(0).getId();

        TestSecurityHelper.loginAs(adminUser);

        assertThatThrownBy(() -> doctorScheduleService.deactivate(scheduleId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expirable_byAge_andByStartTime_butNotFreshOnes() {
        Long fresh = book(slots.get(0));
        Long old = book(slots.get(2));
        Long soon = book(available(doctor, dayAfter).get(0));

        // createdAt có updatable=false nên phải sửa bằng SQL
        jdbc.update("UPDATE appointments SET created_at = ? WHERE id = ?", LocalDateTime.now().minusHours(25), old);
        jdbc.update("UPDATE appointments SET appointment_time = ? WHERE id = ?", LocalDateTime.now().plusMinutes(10), soon);

        assertThat(appointmentService.findExpirablePendingIds())
                .containsExactlyInAnyOrder(old, soon)
                .doesNotContain(fresh);
    }

    @Test
    void reschedule_resetsReminderFlags() {
        Long id = book(slots.get(0));
        TestSecurityHelper.loginAs(adminUser);
        appointmentService.confirm(id);

        Appointment confirmed = appt(id);
        confirmed.setReminder24hSent(true);
        confirmed.setReminder1hSent(true);
        appointmentRepository.save(confirmed);

        TestSecurityHelper.loginAs(patientUser);
        appointmentService.reschedule(id,
                RescheduleRequest.builder().newTimeSlotId(slots.get(2).getId()).reason("doi gio").build());

        assertThat(appt(id).isReminder24hSent()).isFalse();
        assertThat(appt(id).isReminder1hSent()).isFalse();
    }

}
