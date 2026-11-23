package com.example.clinic.service.impl;

import com.example.clinic.booking.config.BookingProperties;
import com.example.clinic.booking.event.AppointmentConfirmedEvent;
import com.example.clinic.dto.request.CreatePaymentRequest;
import com.example.clinic.dto.response.PaymentResponse;
import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.Payment;
import com.example.clinic.entity.PaymentMethod;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.entity.enums.AuditActionType;
import com.example.clinic.entity.enums.PaymentStatus;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.mapper.PaymentMapper;
import com.example.clinic.payment.event.PaymentRefundRequestedEvent;
import com.example.clinic.payment.event.PaymentSucceededEvent;
import com.example.clinic.payment.gateway.GatewayCallback;
import com.example.clinic.payment.gateway.PaymentGatewayService;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.PaymentMethodRepository;
import com.example.clinic.repository.PaymentRepository;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final String DEFAULT_GATEWAY = "VNPAY";
    private static final DateTimeFormatter TXN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository paymentRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final AppointmentRepository appointmentRepository;
    private final BookingProperties bookingProperties;
    private final PaymentMapper paymentMapper;
    private final AuditLogService auditLogService;
    private final List<PaymentGatewayService> gateways;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;   // tools.jackson.databind.ObjectMapper

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, String clientIp) {
        String gatewayCode = (request.getGateway() == null || request.getGateway().isBlank())
                ? DEFAULT_GATEWAY
                : request.getGateway().trim().toUpperCase();
        PaymentGatewayService gateway = resolveGateway(gatewayCode);

        // Khoá dòng lịch hẹn cho tới hết transaction
        Appointment appointment = appointmentRepository.findByIdForUpdate(request.getAppointmentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch hẹn với id: " + request.getAppointmentId()));

        validatePermission(appointment);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể thanh toán trực tuyến khi lịch hẹn đang chờ xác nhận");
        }

        Long appointmentId = appointment.getId();
        LocalDateTime now = LocalDateTime.now();

        if (paymentRepository.existsByAppointmentIdAndStatusIn(appointmentId, List.of(PaymentStatus.SUCCESS))) {
            throw new IllegalStateException("Lịch hẹn này đã được thanh toán");
        }

        // Đã có giao dịch còn hạn → trả lại chính nó, không tạo giao dịch thứ hai
        Optional<Payment> existing = paymentRepository
                .findFirstByAppointmentIdAndStatusAndExpiredAtAfter(appointmentId, PaymentStatus.PENDING, now);
        if (existing.isPresent()) {
            return paymentMapper.toResponse(existing.get());
        }

        // Lịch hẹn sắp bị Pending Expiry thu hồi thì không cho tạo giao dịch mới
        LocalDateTime confirmDeadline = bookingProperties.confirmDeadline(
                appointment.getCreatedAt(), appointment.getAppointmentTime());
        if (confirmDeadline.isBefore(now)) {
            throw new IllegalStateException(
                    "Lịch hẹn đã quá hạn chờ xác nhận, không thể tạo giao dịch thanh toán mới");
        }

        BigDecimal fee = appointment.getDoctor().getConsultationFee();
        if (fee == null || fee.signum() <= 0) {
            throw new IllegalStateException("Bác sĩ này không thu phí khám trực tuyến");
        }

        PaymentMethod method = paymentMethodRepository.findByCodeAndActiveTrue(gatewayCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Phương thức thanh toán chưa được kích hoạt: " + gatewayCode));

        Payment payment = Payment.builder()
                .appointment(appointment)
                .paymentMethod(method)
                .transactionCode(generateTransactionCode(now))
                .amount(fee.setScale(0, RoundingMode.HALF_UP))
                .status(PaymentStatus.PENDING)
                .expiredAt(now.plusMinutes(gateway.paymentExpireMinutes()))
                .build();
        payment = paymentRepository.save(payment);

        // OrderInfo: tiếng Việt không dấu, không ký tự đặc biệt (yêu cầu của VNPay)
        String orderInfo = "Thanh toan phi kham benh " + appointment.getBookingCode().replace("-", "");
        payment.setPaymentUrl(gateway.buildPaymentUrl(payment, orderInfo, clientIp));

        auditLogService.record(
                AuditActionType.PAYMENT_CREATED, "PAYMENT", payment.getId(),
                "Tạo giao dịch " + gatewayCode + " " + payment.getTransactionCode()
                        + " cho lịch hẹn " + appointment.getBookingCode(),
                null,
                Map.of("amount", payment.getAmount(), "status", PaymentStatus.PENDING, "gateway", gatewayCode)
        );

        return paymentMapper.toResponse(payment);
    }

    @Override
    public Map<String, String> handleVnPayIpn(Map<String, String> params) {
        GatewayCallback callback = resolveGateway(DEFAULT_GATEWAY).parseCallback(params);
        if (!callback.signatureValid()) {
            return ipnResponse("97", "Invalid signature");
        }

        Optional<Long> appointmentId =
                paymentRepository.findAppointmentIdByTransactionCode(callback.transactionCode());
        if (appointmentId.isEmpty()) {
            return ipnResponse("01", "Order not found");
        }

        return transactionTemplate.execute(status -> applyVnPayCallback(appointmentId.get(), callback));
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getByTransactionCode(String transactionCode) {
        Payment payment = paymentRepository.findByTransactionCode(transactionCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy giao dịch: " + transactionCode));
        validatePermission(payment.getAppointment());
        return paymentMapper.toResponse(payment);
    }

// ===== IPN helpers =====

    private Map<String, String> applyVnPayCallback(Long appointmentId, GatewayCallback cb) {
        // Thứ tự khoá: appointment → payment (giống createPayment) để không deadlock
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn " + appointmentId));
        Payment payment = paymentRepository.findByTransactionCodeForUpdate(cb.transactionCode())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch " + cb.transactionCode()));

        if (cb.amount() == null || payment.getAmount().compareTo(cb.amount()) != 0) {
            return ipnResponse("04", "Invalid amount");
        }

        PaymentStatus before = payment.getStatus();
        if (before == PaymentStatus.SUCCESS || before == PaymentStatus.REFUNDED
                || before == PaymentStatus.REFUND_FAILED) {
            return ipnResponse("02", "Order already confirmed");
        }

        payment.setGatewayCode(cb.gatewayTransactionNo());
        payment.setGatewayResponse(toJson(cb.data()));

        if (!cb.success()) {
            if (before != PaymentStatus.PENDING) {
                return ipnResponse("02", "Order already confirmed");
            }
            payment.setStatus(PaymentStatus.FAILED);
            auditStatusChange(payment, before,
                    "VNPay báo giao dịch " + payment.getTransactionCode()
                            + " không thành công (mã " + cb.responseCode() + ")");
            return ipnResponse("00", "Confirm Success");
        }

        // Thanh toán thành công. Trạng thái trước đó có thể là PENDING, FAILED (khách thử lại trên trang VNPay)
        // hoặc CANCELLED (thanh toán trễ sau khi giao dịch bị thu hồi)
        boolean paidTwice = paymentRepository.existsByAppointmentIdAndStatusIn(
                appointmentId, List.of(PaymentStatus.SUCCESS));
        boolean appointmentUsable = appointment.getStatus() == AppointmentStatus.PENDING
                || appointment.getStatus() == AppointmentStatus.CONFIRMED;

// Khoản thu phải đúng bằng phí khám hiện tại của lịch hẹn (bác sĩ có thể đã đổi do đổi lịch)
        BigDecimal currentFee = appointment.getDoctor().getConsultationFee();
        boolean feeMatches = currentFee != null
                && payment.getAmount().compareTo(currentFee.setScale(0, RoundingMode.HALF_UP)) == 0;

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(cb.paidAt() != null ? cb.paidAt() : LocalDateTime.now());

        if (paidTwice || !appointmentUsable || !feeMatches) {
            String reason = paidTwice
                    ? "Lịch hẹn đã có giao dịch thanh toán khác thành công"
                    : !appointmentUsable
                      ? "Lịch hẹn đã ở trạng thái " + appointment.getStatus()
                      : "Phí khám hiện tại của lịch hẹn khác số tiền đã thanh toán";
            auditStatusChange(payment, before,
                    "Nhận tiền giao dịch " + payment.getTransactionCode() + " nhưng cần hoàn lại: " + reason);
            eventPublisher.publishEvent(new PaymentRefundRequestedEvent(payment.getId(), reason));
        } else {
            auditStatusChange(payment, before,
                    "Thanh toán thành công giao dịch " + payment.getTransactionCode());
            eventPublisher.publishEvent(new PaymentSucceededEvent(payment.getId()));

            // A: đã thu tiền thì tự xác nhận lịch, cùng transaction với IPN để scheduler hết hạn không chen vào được
            if (appointment.getStatus() == AppointmentStatus.PENDING) {
                appointment.setStatus(AppointmentStatus.CONFIRMED);
                appointmentRepository.save(appointment);
                eventPublisher.publishEvent(new AppointmentConfirmedEvent(appointment.getId()));
            }
        }
        return ipnResponse("00", "Confirm Success");
    }

    private void auditStatusChange(Payment payment, PaymentStatus before, String description) {
        auditLogService.recordSystem(
                AuditActionType.PAYMENT_STATUS_CHANGED, "PAYMENT", payment.getId(), description,
                Map.of("status", before), Map.of("status", payment.getStatus()));
    }

    private Map<String, String> ipnResponse(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findOverduePendingPaymentIds() {
        return paymentRepository.findOverdueIds(PaymentStatus.PENDING, LocalDateTime.now());
    }

    // Không có @Transactional: dùng TransactionTemplate để câu truy vấn đầu tiên trong transaction là câu khoá
    @Override
    public void expireOverduePayment(Long paymentId) {
        Optional<Long> appointmentId = paymentRepository.findAppointmentIdByPaymentId(paymentId);
        if (appointmentId.isEmpty()) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            // Thứ tự khoá: appointment → payment (cùng createPayment và IPN)
            appointmentRepository.findByIdForUpdate(appointmentId.get());
            Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);

            // Kiểm tra lại dưới khoá: có thể IPN vừa chuyển giao dịch sang SUCCESS/FAILED
            if (payment == null
                    || payment.getStatus() != PaymentStatus.PENDING
                    || payment.getExpiredAt() == null
                    || !payment.getExpiredAt().isBefore(LocalDateTime.now())) {
                return;
            }

            payment.setStatus(PaymentStatus.CANCELLED);
            auditStatusChange(payment, PaymentStatus.PENDING,
                    "Giao dịch " + payment.getTransactionCode() + " hết hạn, hệ thống tự huỷ");
        });
    }

    // ===== Private helpers =====

    private PaymentGatewayService resolveGateway(String code) {
        return gateways.stream()
                .filter(g -> g.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cổng thanh toán không được hỗ trợ: " + code));
    }

    // Admin/lễ tân hỗ trợ được; bệnh nhân chỉ thanh toán cho hồ sơ của mình hoặc hồ sơ người thân
    private void validatePermission(Appointment appointment) {
        if (SecurityUtil.isAdminOrReceptionist()) return;
        if (SecurityUtil.isPatient() && appointment.getPatient().isOwnedByUser(SecurityUtil.getCurrentUserId())) return;
        throw new AccessDeniedException("Bạn không có quyền thanh toán cho lịch hẹn này");
    }

    // Chỉ chữ và số, duy nhất trong ngày (VNPay yêu cầu); unique constraint ở DB là lưới an toàn
    private String generateTransactionCode(LocalDateTime now) {
        return "PAY" + now.format(TXN_TIME) + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
    }

}
