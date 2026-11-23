package com.example.clinic.payment.listener;

import com.example.clinic.booking.event.AppointmentCancelledEvent;
import com.example.clinic.booking.event.AppointmentExpiredEvent;
import com.example.clinic.booking.event.AppointmentNoShowEvent;
import com.example.clinic.booking.event.AppointmentRescheduledEvent;
import com.example.clinic.payment.event.PaymentRefundRequestedEvent;
import com.example.clinic.service.PaymentRefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Chính sách: huỷ, hết hạn, bác sĩ nghỉ hoàn 100%; bệnh nhân không đến hoàn theo payment.no-show-refund-percent.
// Chạy sau commit và bất đồng bộ để lỗi mạng sang VNPay không làm hỏng nghiệp vụ lịch hẹn.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentRefundListener {

    private final PaymentRefundService refundService;

    @Value("${payment.no-show-refund-percent:70}")
    private int noShowRefundPercent;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(AppointmentCancelledEvent event) {
        settle(event.appointmentId(), 100, "Lịch hẹn bị huỷ");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExpired(AppointmentExpiredEvent event) {
        settle(event.appointmentId(), 100, "Lịch hẹn hết hạn chờ xác nhận");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoShow(AppointmentNoShowEvent event) {
        settle(event.appointmentId(), noShowRefundPercent, "Bệnh nhân không đến khám");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefundRequested(PaymentRefundRequestedEvent event) {
        try {
            refundService.refund(event.paymentId(), 100, event.reason());
        } catch (RuntimeException e) {
            log.error("Hoàn tiền giao dịch {} thất bại", event.paymentId(), e);
        }
    }

    private void settle(Long appointmentId, int percent, String reason) {
        try {
            refundService.settleAppointmentPayments(appointmentId, percent, reason);
        } catch (RuntimeException e) {
            log.error("Xử lý thanh toán cho lịch hẹn {} thất bại", appointmentId, e);
        }
    }

    // Đổi sang bác sĩ khác phí: giao dịch đang chờ đang ghi sai số tiền nên hủy.
    // Nếu khách vẫn trả trễ, IPN sẽ tự hoàn vì số tiền không khớp phí hiện tại.
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRescheduled(AppointmentRescheduledEvent event) {
        if (!event.feeChanged()) return;
        try {
            refundService.cancelPendingPayments(event.appointmentId());
        } catch (RuntimeException e) {
            log.error("Hủy giao dịch chờ cho lịch hẹn {} thất bại", event.appointmentId(), e);
        }
    }
}
