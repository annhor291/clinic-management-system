package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.request.CreatePaymentRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.PaymentListItemResponse;
import com.example.clinic.dto.response.PaymentResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.*;
import com.example.clinic.payment.config.VnPayProperties;
import com.example.clinic.payment.event.PaymentRefundRequestedEvent;
import com.example.clinic.payment.gateway.VnPayApiClient;
import com.example.clinic.payment.gateway.VnPayUtil;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RecordApplicationEvents
public class PaymentIntegrationTest extends BaseIntegrationTest {

    @Autowired private PaymentService paymentService;
    @Autowired
    private AppointmentService appointmentService;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentMethodRepository paymentMethodRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private VnPayProperties vnPayProperties;
    @Autowired private ApplicationEvents applicationEvents;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private PaymentRefundService paymentRefundService;

    @MockitoBean
    private VnPayApiClient vnPayApiClient;


    @MockitoBean
    private EmailService emailService;

    private User patientUser;
    private User otherUser;
    private User adminUser;
    private Doctor doctor;
    private Long appointmentId;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);

        paymentMethodRepository.save(PaymentMethod.builder().name("VNPay").code("VNPAY").active(true).build());

        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Noi khoa - payment test").active(true).build());

        User doctorUser = userRepository.save(User.builder()
                .username("doctor_pay_test").email("doctor_pay_test@example.com")
                .password("x").role(Role.DOCTOR).provider(AuthProvider.LOCAL).enabled(true).build());
        doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser).specialty(specialty).fullName("BS Payment Test")
                .consultationFee(new BigDecimal("200000")).active(true).build());

        patientUser = userRepository.save(User.builder()
                .username("patient_pay_test").email("patient_pay_test@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
        Patient patient = patientRepository.save(Patient.builder()
                .user(patientUser).fullName("Benh nhan Payment Test").build());

        otherUser = userRepository.save(User.builder()
                .username("other_pay_test").email("other_pay_test@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
        adminUser = userRepository.save(User.builder()
                .username("admin_pay_test").email("admin_pay_test@example.com")
                .password("x").role(Role.ADMIN).provider(AuthProvider.LOCAL).enabled(true).build());

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0), ShiftType.MORNING);
        TimeSlot slot = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), tomorrow, SlotStatus.AVAILABLE).get(0);

        TestSecurityHelper.loginAs(patientUser);
        appointmentId = appointmentService.book(AppointmentRequest.builder()
                .patientId(patient.getId()).timeSlotId(slot.getId()).build()).getId();
    }

    private CreatePaymentRequest request() {
        return CreatePaymentRequest.builder().appointmentId(appointmentId).build();
    }

    @Test
    void createPayment_shouldReturnSignedUrl_andSavePendingPayment_andAudit() {
        PaymentResponse res = paymentService.createPayment(request(), "127.0.0.1");

        assertThat(res.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(res.getAmount()).isEqualByComparingTo("200000");
        assertThat(res.getPaymentUrl()).contains("vnp_Amount=20000000").contains("vnp_SecureHash=");

        Payment saved = paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getExpiredAt()).isAfter(saved.getCreatedAt());

        assertThat(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "PAYMENT", saved.getId(), PageRequest.of(0, 5)).getContent())
                .anyMatch(l -> l.getAction() == AuditActionType.PAYMENT_CREATED);
    }

    @Test
    void createPayment_calledTwice_shouldReuseSameTransaction() {
        PaymentResponse first = paymentService.createPayment(request(), "127.0.0.1");
        PaymentResponse second = paymentService.createPayment(request(), "127.0.0.1");

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(paymentRepository.findAll()).hasSize(1);
    }

    @Test
    void createPayment_shouldRejectConfirmedAppointment() {
        TestSecurityHelper.loginAs(adminUser);
        appointmentService.confirm(appointmentId);
        TestSecurityHelper.loginAs(patientUser);

        assertThatThrownBy(() -> paymentService.createPayment(request(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createPayment_shouldRejectOtherUsersAppointment() {
        TestSecurityHelper.loginAs(otherUser);

        assertThatThrownBy(() -> paymentService.createPayment(request(), "127.0.0.1"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void createPayment_shouldRejectWhenAlreadyPaid() {
        Appointment appt = appointmentRepository.findById(appointmentId).orElseThrow();
        PaymentMethod method = paymentMethodRepository.findByCodeAndActiveTrue("VNPAY").orElseThrow();
        paymentRepository.save(Payment.builder()
                .appointment(appt).paymentMethod(method)
                .transactionCode("PAYDONE1").amount(new BigDecimal("200000"))
                .status(PaymentStatus.SUCCESS).build());

        assertThatThrownBy(() -> paymentService.createPayment(request(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã được thanh toán");
    }

    @Test
    void createPayment_shouldRejectZeroFee() {
        Doctor d = doctorRepository.findById(doctor.getId()).orElseThrow();
        d.setConsultationFee(BigDecimal.ZERO);
        doctorRepository.save(d);

        assertThatThrownBy(() -> paymentService.createPayment(request(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class);
    }

    // IPN do VNPay gọi, không có người đăng nhập
    private Map<String, String> signedIpn(String txnRef, String amountX100, String responseCode, String txnStatus) {
        Map<String, String> d = new HashMap<>();
        d.put("vnp_TmnCode", "TESTCODE");
        d.put("vnp_TxnRef", txnRef);
        d.put("vnp_Amount", amountX100);
        d.put("vnp_ResponseCode", responseCode);
        d.put("vnp_TransactionStatus", txnStatus);
        d.put("vnp_TransactionNo", "14123456");
        d.put("vnp_PayDate", "20261020103000");
        Map<String, String> m = new HashMap<>(d);
        m.put("vnp_SecureHash", VnPayUtil.hmacSha512(vnPayProperties.getHashSecret(), VnPayUtil.buildSignedData(d)));
        return m;
    }

    private PaymentResponse createPending() {
        return paymentService.createPayment(request(), "127.0.0.1");
    }

    @Test
    void ipn_success_shouldMarkPaid_andAuditAsSystem() {
        PaymentResponse res = createPending();
        SecurityContextHolder.clearContext();

        Map<String, String> rsp = paymentService.handleVnPayIpn(
                signedIpn(res.getTransactionCode(), "20000000", "00", "00"));

        assertThat(rsp.get("RspCode")).isEqualTo("00");
        Payment saved = paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(saved.getPaidAt()).isNotNull();
        assertThat(saved.getGatewayCode()).isEqualTo("14123456");
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CONFIRMED);   // đã thu tiền thì tự xác nhận lịch

        assertThat(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "PAYMENT", saved.getId(), PageRequest.of(0, 10)).getContent())
                .anyMatch(l -> l.getAction() == AuditActionType.PAYMENT_STATUS_CHANGED && l.getUser() == null);
        assertThat(applicationEvents.stream(PaymentRefundRequestedEvent.class).count()).isZero();
    }

    @Test
    void ipn_sameCallbackTwice_shouldBeIdempotent() {
        PaymentResponse res = createPending();
        SecurityContextHolder.clearContext();
        Map<String, String> params = signedIpn(res.getTransactionCode(), "20000000", "00", "00");

        assertThat(paymentService.handleVnPayIpn(params).get("RspCode")).isEqualTo("00");
        assertThat(paymentService.handleVnPayIpn(params).get("RspCode")).isEqualTo("02");

        Payment saved = paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                        "PAYMENT", saved.getId(), PageRequest.of(0, 10)).getContent()
                .stream().filter(l -> l.getAction() == AuditActionType.PAYMENT_STATUS_CHANGED).count())
                .isEqualTo(1);
    }

    @Test
    void ipn_invalidSignature_wrongAmount_unknownOrder_shouldBeRejected() {
        PaymentResponse res = createPending();
        SecurityContextHolder.clearContext();

        Map<String, String> tampered = signedIpn(res.getTransactionCode(), "20000000", "00", "00");
        tampered.put("vnp_Amount", "100");
        assertThat(paymentService.handleVnPayIpn(tampered).get("RspCode")).isEqualTo("97");

        assertThat(paymentService.handleVnPayIpn(
                signedIpn(res.getTransactionCode(), "100", "00", "00")).get("RspCode")).isEqualTo("04");

        assertThat(paymentService.handleVnPayIpn(
                signedIpn("PAYKHONGTONTAI", "20000000", "00", "00")).get("RspCode")).isEqualTo("01");

        assertThat(paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void ipn_failure_shouldMarkFailed_thenAllowNewPayment() {
        PaymentResponse res = createPending();
        SecurityContextHolder.clearContext();

        assertThat(paymentService.handleVnPayIpn(
                signedIpn(res.getTransactionCode(), "20000000", "24", "02")).get("RspCode")).isEqualTo("00");
        assertThat(paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        TestSecurityHelper.loginAs(patientUser);
        PaymentResponse retry = createPending();
        assertThat(retry.getId()).isNotEqualTo(res.getId());
    }

    @Test
    void ipn_lateSuccessOnCancelledAppointment_shouldRecordPaid_andRequestRefund() {
        PaymentResponse res = createPending();
        Appointment appt = appointmentRepository.findById(appointmentId).orElseThrow();
        appt.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.save(appt);
        SecurityContextHolder.clearContext();

        assertThat(paymentService.handleVnPayIpn(
                signedIpn(res.getTransactionCode(), "20000000", "00", "00")).get("RspCode")).isEqualTo("00");

        assertThat(paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow().getStatus())
                .isIn(PaymentStatus.SUCCESS, PaymentStatus.REFUNDED, PaymentStatus.REFUND_FAILED);
        assertThat(applicationEvents.stream(PaymentRefundRequestedEvent.class).count()).isEqualTo(1);
    }

    @Test
    void getByTransactionCode_shouldCheckOwnership() {
        PaymentResponse res = createPending();

        assertThat(paymentService.getByTransactionCode(res.getTransactionCode()).getId()).isEqualTo(res.getId());

        TestSecurityHelper.loginAs(otherUser);
        assertThatThrownBy(() -> paymentService.getByTransactionCode(res.getTransactionCode()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void expireOverduePayment_shouldCancelPendingPayment_andAuditAsSystem() {
        PaymentResponse res = paymentService.createPayment(request(), "127.0.0.1");
        Payment p = paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();
        p.setExpiredAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(p);

        assertThat(paymentService.findOverduePendingPaymentIds()).contains(p.getId());
        paymentService.expireOverduePayment(p.getId());

        assertThat(paymentRepository.findById(p.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.CANCELLED);
        assertThat(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "PAYMENT", p.getId(), PageRequest.of(0, 10)).getContent())
                .anyMatch(l -> l.getAction() == AuditActionType.PAYMENT_STATUS_CHANGED && l.getUser() == null);
    }

    @Test
    void expireOverduePayment_shouldNotTouchPaidOrLivePayment() {
        PaymentResponse res = paymentService.createPayment(request(), "127.0.0.1");
        Payment p = paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();

        // Còn hạn → bỏ qua
        paymentService.expireOverduePayment(p.getId());
        assertThat(paymentRepository.findById(p.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);

        // Đã thanh toán dù expiredAt đã qua → bỏ qua
        p = paymentRepository.findById(p.getId()).orElseThrow();
        p.setStatus(PaymentStatus.SUCCESS);
        p.setExpiredAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(p);
        paymentService.expireOverduePayment(p.getId());
        assertThat(paymentRepository.findById(p.getId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void ruleA_appointmentWithLivePayment_shouldNotExpire_untilPaymentExpires() {
        PaymentResponse res = paymentService.createPayment(request(), "127.0.0.1");

        // Payment còn hạn → lịch hẹn không bị expire
        appointmentService.expireOne(appointmentId);
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.PENDING);

        // Payment hết hạn và bị huỷ → lịch hẹn expire được, slot được giải phóng
        Payment p = paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();
        p.setExpiredAt(LocalDateTime.now().minusMinutes(1));
        paymentRepository.save(p);
        paymentService.expireOverduePayment(p.getId());

        appointmentService.expireOne(appointmentId);
        Appointment appt = appointmentRepository.findById(appointmentId).orElseThrow();
        assertThat(appt.getStatus()).isEqualTo(AppointmentStatus.EXPIRED);
        assertThat(timeSlotRepository.findById(appt.getTimeSlot().getId()).orElseThrow().getStatus())
                .isEqualTo(SlotStatus.AVAILABLE);
    }

    @Test
    void expireOne_shouldCreateExpiredNotification() {
        appointmentService.expireOne(appointmentId);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.findAll())
                        .anyMatch(n -> n.getType() == NotificationType.APPOINTMENT_EXPIRED));
    }

    private Payment paidPayment() {
        PaymentResponse res = createPending();
        SecurityContextHolder.clearContext();
        paymentService.handleVnPayIpn(signedIpn(res.getTransactionCode(), "20000000", "00", "00"));
        TestSecurityHelper.loginAs(patientUser);
        return paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();
    }

    private Map<String, String> signedRefundResponse(String txnRef, String amountX100, String code, String status) {
        Map<String, String> r = new HashMap<>();
        r.put("vnp_ResponseId", "RESP1");
        r.put("vnp_Command", "refund");
        r.put("vnp_ResponseCode", code);
        r.put("vnp_Message", "Refund request");
        r.put("vnp_TmnCode", "TESTCODE");
        r.put("vnp_TxnRef", txnRef);
        r.put("vnp_Amount", amountX100);
        r.put("vnp_BankCode", "NCB");
        r.put("vnp_PayDate", "20261020110000");
        r.put("vnp_TransactionNo", "99887766");
        r.put("vnp_TransactionType", "02");
        r.put("vnp_TransactionStatus", status);
        r.put("vnp_OrderInfo", "Hoan tien");
        r.put("vnp_SecureHash", VnPayUtil.hmacSha512(vnPayProperties.getHashSecret(), VnPayUtil.refundResponseData(r)));
        return r;
    }

    private Payment reload(Payment p) {
        return paymentRepository.findById(p.getId()).orElseThrow();
    }

    @Test
    @SuppressWarnings("unchecked")
    void refund_full_shouldSendCorrectRequest_andMarkRefunded() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "00", "05"));

        paymentRefundService.refund(p.getId(), 100, "Test hoan tien");

        ArgumentCaptor<Map<String, String>> body = ArgumentCaptor.forClass(Map.class);
        verify(vnPayApiClient).postJson(any(), body.capture());
        assertThat(body.getValue())
                .containsEntry("vnp_Command", "refund")
                .containsEntry("vnp_TransactionType", "02")
                .containsEntry("vnp_Amount", "20000000")
                .containsEntry("vnp_TxnRef", p.getTransactionCode())
                .containsEntry("vnp_TransactionNo", "14123456")
                .containsEntry("vnp_TransactionDate", p.getGatewayCreateDate())
                .containsKey("vnp_SecureHash");
        assertThat(p.getPaymentUrl()).contains("vnp_CreateDate=" + p.getGatewayCreateDate());

        Payment after = reload(p);
        assertThat(after.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(after.getRefundAmount()).isEqualByComparingTo("200000");
        assertThat(after.getRefundedAt()).isNotNull();
        assertThat(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "PAYMENT", p.getId(), PageRequest.of(0, 10)).getContent())
                .anyMatch(l -> l.getAction() == AuditActionType.PAYMENT_STATUS_CHANGED && l.getUser() == null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void refund_partial70_shouldSendTypeO3_andRefundSeventyPercent() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "14000000", "00", "05"));

        paymentRefundService.refund(p.getId(), 70, "Khong den kham");

        ArgumentCaptor<Map<String, String>> body = ArgumentCaptor.forClass(Map.class);
        verify(vnPayApiClient).postJson(any(), body.capture());
        assertThat(body.getValue())
                .containsEntry("vnp_TransactionType", "03")
                .containsEntry("vnp_Amount", "14000000");
        assertThat(reload(p).getRefundAmount()).isEqualByComparingTo("140000");
    }

    @Test
    void refund_rejectedByVnPay_shouldMarkRefundFailed() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "00", "09"));

        paymentRefundService.refund(p.getId(), 100, "Test");

        assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    @Test
    void refund_alreadyRequestedCode94_shouldCountAsAccepted() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "94", "05"));

        paymentRefundService.refund(p.getId(), 100, "Test");

        assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refund_tamperedResponse_shouldMarkRefundFailed() {
        Payment p = paidPayment();
        Map<String, String> tampered = signedRefundResponse(p.getTransactionCode(), "20000000", "00", "05");
        tampered.put("vnp_Amount", "1");
        when(vnPayApiClient.postJson(any(), any())).thenReturn(tampered);

        paymentRefundService.refund(p.getId(), 100, "Test");

        assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
    }

    @Test
    void refund_networkError_shouldMarkRefundFailed_thenRetryReusesSameRequestId() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "00", "05"));

        paymentRefundService.refund(p.getId(), 100, "Test");
        Payment failed = reload(p);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);
        String requestId = failed.getRefundRequestId();
        assertThat(requestId).isNotBlank().hasSizeLessThanOrEqualTo(32);

        paymentRefundService.refund(p.getId(), 100, "Test");   // admin thử lại
        Payment retried = reload(p);
        assertThat(retried.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(retried.getRefundRequestId()).isEqualTo(requestId);
    }

    @Test
    void refund_calledTwice_shouldCallVnPayOnlyOnce() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "00", "05"));

        paymentRefundService.refund(p.getId(), 100, "Test");
        paymentRefundService.refund(p.getId(), 100, "Test");

        verify(vnPayApiClient, times(1)).postJson(any(), any());
    }

    @Test
    void refund_unpaidPayment_shouldNotCallVnPay() {
        PaymentResponse res = createPending();
        Payment p = paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow();

        paymentRefundService.refund(p.getId(), 100, "Test");

        verify(vnPayApiClient, never()).postJson(any(), any());
        assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void cancelAppointment_shouldRefundPaidPayment_automatically() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "00", "05"));

        CancelRequest req = new CancelRequest();
        req.setReason("Ban viec dot xuat");
        appointmentService.cancel(appointmentId, req);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.REFUNDED));
    }

    @Test
    void cancelAppointment_shouldCancelPendingPayment() {
        PaymentResponse res = createPending();

        CancelRequest req = new CancelRequest();
        req.setReason("Ban viec dot xuat");
        appointmentService.cancel(appointmentId, req);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(paymentRepository.findByTransactionCode(res.getTransactionCode()).orElseThrow().getStatus())
                        .isEqualTo(PaymentStatus.CANCELLED));
    }

    @Test
    void expireOne_shouldNotTouchPaidAppointment_becauseItIsAlreadyConfirmed() {
        Payment p = paidPayment();   // IPN thành công → lịch tự CONFIRMED

        appointmentService.expireOne(appointmentId);

        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    // Lưới an toàn: nếu vì lý do nào đó lịch đã thu tiền vẫn PENDING rồi hết hạn thì vẫn hoàn tiền
    @Test
    void expireOne_withPaidButPendingAppointment_shouldStillRefund() {
        Payment p = paidPayment();
        Appointment appt = appointmentRepository.findById(appointmentId).orElseThrow();
        appt.setStatus(AppointmentStatus.PENDING);
        appointmentRepository.save(appt);
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "00", "05"));

        appointmentService.expireOne(appointmentId);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.REFUNDED));
    }

    private boolean hasNotification(NotificationType type) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(patientUser.getId(), PageRequest.of(0, 50))
                .getContent().stream().anyMatch(n -> n.getType() == type);
    }

    @Test
    void ipnSuccess_shouldCreatePaymentSuccessNotification_andExposePaymentStatus() {
        Payment p = paidPayment();

        await().atMost(5, SECONDS).until(() -> hasNotification(NotificationType.PAYMENT_SUCCESS));

        assertThat(paymentRepository.findFirstByAppointmentIdOrderByCreatedAtDescIdDesc(p.getAppointment().getId())
                .orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }

    @Test
    void refund_success_shouldCreateRefundedNotification() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "20000000", "00", "05"));

        paymentRefundService.refund(p.getId(), 100, "Test hoan tien");

        await().atMost(5, SECONDS).until(() -> hasNotification(NotificationType.PAYMENT_REFUNDED));
    }

    @Test
    void retryFailedRefund_shouldKeepAmount_andMarkRefundedWhenGatewayAccepts() {
        Payment p = paidPayment();
        when(vnPayApiClient.postJson(any(), any()))
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "14000000", "00", "09"))   // lần 1: bị từ chối
                .thenReturn(signedRefundResponse(p.getTransactionCode(), "14000000", "00", "05")); // lần 2: chấp nhận

        paymentRefundService.refund(p.getId(), 70, "Khong den kham");
        assertThat(reload(p).getStatus()).isEqualTo(PaymentStatus.REFUND_FAILED);

        PaymentStatus result = paymentRefundService.retryFailedRefund(p.getId());

        assertThat(result).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(reload(p).getRefundAmount()).isEqualByComparingTo("140000");   // vẫn 70%, không tính lại
        verify(vnPayApiClient, times(2)).postJson(any(), any());
    }

    @Test
    void retryFailedRefund_onPaymentNotFailed_shouldThrow() {
        Payment p = paidPayment();   // đang SUCCESS

        assertThatThrownBy(() -> paymentRefundService.retryFailedRefund(p.getId()))
                .isInstanceOf(IllegalStateException.class);
        verify(vnPayApiClient, never()).postJson(any(), any());
    }

    @Test
    void ipn_feeChangedAfterPayment_shouldRequestRefund_andNotConfirm() {
        PaymentResponse res = createPending();
        Doctor d = doctorRepository.findById(doctor.getId()).orElseThrow();
        d.setConsultationFee(new BigDecimal("300000"));
        doctorRepository.save(d);
        SecurityContextHolder.clearContext();

        assertThat(paymentService.handleVnPayIpn(
                signedIpn(res.getTransactionCode(), "20000000", "00", "00")).get("RspCode")).isEqualTo("00");

        assertThat(applicationEvents.stream(PaymentRefundRequestedEvent.class).count()).isEqualTo(1);
        assertThat(appointmentRepository.findById(appointmentId).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.PENDING);
    }

    @Test
    void listForAdmin_shouldReturnPaymentsWithPatientName_andFilterByStatus() {
        paymentService.createPayment(request(), "127.0.0.1");

        TestSecurityHelper.loginAs(adminUser);
        PageResponse<PaymentListItemResponse> all = paymentService.listForAdmin(null, 0, 10);
        assertThat(all.getTotalElements()).isEqualTo(1);
        PaymentListItemResponse item = all.getContent().get(0);
        assertThat(item.getPatientName()).isEqualTo("Benh nhan Payment Test");
        assertThat(item.getPaymentMethod()).isEqualTo("VNPAY");
        assertThat(item.getStatus()).isEqualTo(PaymentStatus.PENDING);

        assertThat(paymentService.listForAdmin(PaymentStatus.PENDING, 0, 10).getTotalElements()).isEqualTo(1);
        assertThat(paymentService.listForAdmin(PaymentStatus.SUCCESS, 0, 10).getTotalElements()).isZero();
    }

    @Test
    void listForAdmin_shouldRejectNonAdmin() {
        TestSecurityHelper.loginAs(patientUser);
        assertThatThrownBy(() -> paymentService.listForAdmin(null, 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

}
