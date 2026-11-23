package com.example.clinic.service;

import com.example.clinic.entity.enums.PaymentStatus;

public interface PaymentRefundService {

    // Lịch hẹn đã đóng (huỷ, hết hạn, không đến): huỷ giao dịch đang chờ và hoàn tiền giao dịch đã thu
    void settleAppointmentPayments(Long appointmentId, int refundPercent, String reason);

    // Hoàn refundPercent% của 1 giao dịch đã thu. Gọi lặp lại không hoàn trùng.
    void refund(Long paymentId, int refundPercent, String reason);

    // Admin thử lại giao dịch REFUND_FAILED; trả về trạng thái sau khi thử
    PaymentStatus retryFailedRefund(Long paymentId);

    void cancelPendingPayments(Long appointmentId);
}
