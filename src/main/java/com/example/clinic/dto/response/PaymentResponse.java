package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    private Long id;
    private Long appointmentId;
    private String gateway;
    private String transactionCode;
    private BigDecimal amount;
    private PaymentStatus status;
    private String paymentUrl;
    private LocalDateTime expiredAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
}
