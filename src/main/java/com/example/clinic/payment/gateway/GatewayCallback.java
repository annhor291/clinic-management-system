package com.example.clinic.payment.gateway;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

// Kết quả đã xác thực chữ ký của 1 callback từ cổng. amount là số tiền VND thật (đã chia 100).
public record GatewayCallback (

    boolean signatureValid,
    String transactionCode,
    BigDecimal amount,
    boolean success,
    String gatewayTransactionNo,
    String responseCode,
    LocalDateTime paidAt,
    Map<String, String> data) {

}

