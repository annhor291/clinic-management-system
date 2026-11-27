package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Một dòng trong danh sách thanh toán của admin (không chứa paymentUrl hay dữ liệu cổng thanh toán)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentListItemResponse {

    private Long id;
    private Long appointmentId;
    private String transactionCode;
    private String patientName;
    private BigDecimal amount;
    private PaymentStatus status;
    private String paymentMethod;   // mã cổng, vd VNPAY
    private BigDecimal refundAmount;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
