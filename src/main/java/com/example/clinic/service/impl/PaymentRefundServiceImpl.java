package com.example.clinic.service.impl;

import com.example.clinic.entity.Payment;
import com.example.clinic.entity.enums.AuditActionType;
import com.example.clinic.entity.enums.PaymentStatus;
import com.example.clinic.payment.event.PaymentRefundedEvent;
import com.example.clinic.payment.gateway.PaymentGatewayService;
import com.example.clinic.payment.gateway.RefundResult;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.PaymentRepository;
import com.example.clinic.service.AuditLogService;
import com.example.clinic.service.PaymentRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

// Hoàn tiền chia 3 pha để không giữ khoá DB trong lúc gọi HTTP sang VNPay:
// (1) transaction ngắn: khoá, kiểm tra, chốt số tiền và mã yêu cầu
// (2) gọi VNPay, nằm ngoài transaction
// (3) transaction ngắn: ghi kết quả
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRefundServiceImpl implements PaymentRefundService {

    private static final DateTimeFormatter REQUEST_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final PaymentRepository paymentRepository;
    private final AppointmentRepository appointmentRepository;
    private final List<PaymentGatewayService> gateways;
    private final TransactionTemplate transactionTemplate;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    private record RefundPlan(Payment payment, BigDecimal amount, String requestId, String gatewayCode) {
    }

    @Override
    public void settleAppointmentPayments(Long appointmentId, int refundPercent, String reason) {
        cancelPendingPayments(appointmentId);
        paymentRepository.findFirstByAppointmentIdAndStatus(appointmentId, PaymentStatus.SUCCESS)
                .ifPresent(p -> refund(p.getId(), refundPercent, reason));
    }

    @Override
    public void refund(Long paymentId, int refundPercent, String reason) {
        if (refundPercent <= 0 || refundPercent > 100) {
            throw new IllegalArgumentException("Tỉ lệ hoàn tiền phải trong khoảng 1 đến 100");
        }

        RefundPlan plan = transactionTemplate.execute(status -> prepare(paymentId, refundPercent, reason));
        if (plan == null) {
            return;   // chưa thu tiền, đã hoàn rồi, hoặc số tiền hoàn bằng 0
        }

        RefundResult result;
        try {
            result = resolveGateway(plan.gatewayCode())
                    .refund(plan.payment(), plan.amount(), plan.requestId(), "system");
        } catch (RuntimeException e) {
            log.error("Hoàn tiền giao dịch {} lỗi", paymentId, e);
            result = RefundResult.failure("Lỗi khi gọi cổng thanh toán: " + e.getMessage());
        }

        RefundResult finalResult = result;
        transactionTemplate.executeWithoutResult(status -> finish(paymentId, plan.amount(), finalResult));
    }

    @Override
    public PaymentStatus retryFailedRefund(Long paymentId) {
        String reason = transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .orElseThrow(() -> new com.example.clinic.exception.ResourceNotFoundException(
                            "Không tìm thấy giao dịch " + paymentId));
            if (payment.getStatus() != PaymentStatus.REFUND_FAILED) {
                throw new IllegalStateException("Chỉ thử lại được giao dịch có trạng thái REFUND_FAILED");
            }
            // Mã yêu cầu mới: yêu cầu cũ đã bị từ chối, dùng lại có thể bị VNPay trả mã 94 (đã nhận) và ta ghi nhận sai
            payment.setRefundRequestId(null);
            auditStatusChange(payment, PaymentStatus.REFUND_FAILED,
                    "Admin thử lại hoàn tiền giao dịch " + payment.getTransactionCode());
            return payment.getRefundReason();
        });
        refund(paymentId, 100, reason);   // tỉ lệ bị bỏ qua: prepare dùng lại số tiền đã chốt lần đầu
        return paymentRepository.findById(paymentId).orElseThrow().getStatus();
    }

    // ===== Pha 1 =====
    private RefundPlan prepare(Long paymentId, int refundPercent, String reason) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null) {
            return null;
        }
        // SUCCESS: hoàn lần đầu. REFUND_FAILED: thử lại, dùng lại cùng mã yêu cầu nên VNPay không hoàn trùng.
        if (payment.getStatus() != PaymentStatus.SUCCESS && payment.getStatus() != PaymentStatus.REFUND_FAILED) {
            return null;
        }

        // Thử lại (REFUND_FAILED) và đã có số tiền chốt: giữ đúng số tiền đó.
        // Lần đầu (SUCCESS): tính theo tỉ lệ, kể cả khi refundAmount là null hay 0.
        boolean hasLockedAmount = payment.getStatus() == PaymentStatus.REFUND_FAILED
                && payment.getRefundAmount() != null
                && payment.getRefundAmount().signum() > 0;
        BigDecimal amount = hasLockedAmount
                ? payment.getRefundAmount()
                : payment.getAmount()
                  .multiply(BigDecimal.valueOf(refundPercent))
                  .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            return null;
        }
        // lưu số tiền dự định hoàn; trạng thái vẫn là SUCCESS/REFUND_FAILED
        payment.setRefundAmount(amount);

        if (payment.getRefundRequestId() == null) {
            // Tối đa 32 ký tự, không trùng giữa các giao dịch
            payment.setRefundRequestId("RF" + payment.getId() + LocalDateTime.now().format(REQUEST_TIME));
        }
        payment.setRefundReason(reason);

        return new RefundPlan(payment, amount, payment.getRefundRequestId(),
                payment.getPaymentMethod().getCode());
    }

    // ===== Pha 3 =====
    private void finish(Long paymentId, BigDecimal amount, RefundResult result) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null
                || (payment.getStatus() != PaymentStatus.SUCCESS
                && payment.getStatus() != PaymentStatus.REFUND_FAILED)) {
            return;   // luồng khác đã hoàn xong
        }

        PaymentStatus before = payment.getStatus();
        payment.setRefundResponse(result.data().isEmpty() ? result.message() : toJson(result.data()));

        if (result.success()) {
            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundAmount(amount);
            payment.setRefundedAt(LocalDateTime.now());
            auditStatusChange(payment, before, "Đã gửi hoàn " + amount.toPlainString() + " VND cho giao dịch "
                    + payment.getTransactionCode() + " (lý do: " + payment.getRefundReason() + ")");
            eventPublisher.publishEvent(new PaymentRefundedEvent(payment.getId()));
        } else {
            payment.setStatus(PaymentStatus.REFUND_FAILED);
            auditStatusChange(payment, before, "Hoàn tiền giao dịch " + payment.getTransactionCode()
                    + " không thành công, cần admin xử lý: " + result.message()
                    + (result.responseCode() != null ? " (mã " + result.responseCode() + ")" : ""));
        }
    }

    // Lịch hẹn đóng thì giao dịch còn chờ không còn ý nghĩa. Thanh toán trễ (nếu có) đi theo luồng IPN và tự hoàn.
    @Override
    public void cancelPendingPayments(Long appointmentId) {
        transactionTemplate.executeWithoutResult(status -> {
            // Thứ tự khoá: appointment → payment (giống createPayment và IPN)
            if (appointmentRepository.findByIdForUpdate(appointmentId).isEmpty()) {
                return;
            }
            for (Payment payment : paymentRepository.findByAppointmentIdOrderByCreatedAtDesc(appointmentId)) {
                if (payment.getStatus() == PaymentStatus.PENDING) {
                    payment.setStatus(PaymentStatus.CANCELLED);
                    auditStatusChange(payment, PaymentStatus.PENDING,
                            "Lịch hẹn đã đóng, hệ thống huỷ giao dịch " + payment.getTransactionCode());
                }
            }
        });
    }

    private PaymentGatewayService resolveGateway(String code) {
        return gateways.stream()
                .filter(g -> g.code().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Cổng thanh toán không được hỗ trợ: " + code));
    }

    private void auditStatusChange(Payment payment, PaymentStatus before, String description) {
        auditLogService.recordSystem(
                AuditActionType.PAYMENT_STATUS_CHANGED, "PAYMENT", payment.getId(), description,
                Map.of("status", before), Map.of("status", payment.getStatus()));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }
}
