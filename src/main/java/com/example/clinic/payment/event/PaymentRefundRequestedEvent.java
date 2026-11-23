package com.example.clinic.payment.event;

// Phát ra khi đã thu tiền nhưng không được giữ khoản đó (lịch đã huỷ/hết hạn, hoặc thu trùng).
// Step 5 sẽ thêm listener để hoàn tiền tự động.
public record PaymentRefundRequestedEvent(Long paymentId, String reason) {
}
