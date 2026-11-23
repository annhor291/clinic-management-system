package com.example.clinic.payment.listener;

import com.example.clinic.payment.event.PaymentRefundedEvent;
import com.example.clinic.payment.event.PaymentSucceededEvent;
import com.example.clinic.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Thông báo thanh toán và hoàn tiền: chạy sau commit, bất đồng bộ, lỗi gửi không ảnh hưởng nghiệp vụ.
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSucceeded(PaymentSucceededEvent event) {
        try {
            notificationService.notifyPaymentSucceeded(event.paymentId());
        } catch (Exception e) {
            log.error("Lỗi thông báo thanh toán thành công, paymentId={}", event.paymentId(), e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRefunded(PaymentRefundedEvent event) {
        try {
            notificationService.notifyPaymentRefunded(event.paymentId());
        } catch (Exception e) {
            log.error("Lỗi thông báo hoàn tiền, paymentId={}", event.paymentId(), e);
        }
    }
}
