package com.example.clinic.payment.scheduler;

import com.example.clinic.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Định kỳ huỷ các giao dịch PENDING đã quá hạn. Mỗi giao dịch 1 transaction riêng
// để 1 lỗi không ảnh hưởng cả batch.
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpiryScheduler {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.expiry-check-interval-ms:60000}")
    public void cancelOverduePayments() {
        for (Long id : paymentService.findOverduePendingPaymentIds()) {
            try {
                paymentService.expireOverduePayment(id);
            } catch (Exception e) {
                log.error("Lỗi khi huỷ giao dịch quá hạn id={}", id, e);
            }
        }
    }
}
