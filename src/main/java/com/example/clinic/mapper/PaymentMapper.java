package com.example.clinic.mapper;

import com.example.clinic.dto.response.PaymentResponse;
import com.example.clinic.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .appointmentId(p.getAppointment().getId())
                .gateway(p.getPaymentMethod().getCode())
                .transactionCode(p.getTransactionCode())
                .amount(p.getAmount())
                .status(p.getStatus())
                .paymentUrl(p.getPaymentUrl())
                .expiredAt(p.getExpiredAt())
                .paidAt(p.getPaidAt())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
