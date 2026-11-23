package com.example.clinic.payment.gateway;

import java.util.Map;

// Kết quả đã xác thực chữ ký của 1 yêu cầu hoàn tiền. success = cổng đã nhận và không từ chối.
public record RefundResult (

    boolean success,
    String responseCode,
    String transactionStatus,
    String gatewayRefundNo,
    String message,
    Map<String, String> data) {

        public static RefundResult failure(String message) {
            return new RefundResult(false, null,
                    null, null, message, Map.of());
        }
}
