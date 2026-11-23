package com.example.clinic.entity.enums;

public enum PaymentStatus {

    PENDING,        // đã tạo giao dịch, đang chờ khách thanh toán
    SUCCESS,        // cổng xác nhận đã thu tiền
    FAILED,         // cổng báo thất bại
    CANCELLED,      // khách huỷ hoặc giao dịch hết hạn
    REFUNDED,       // đã hoàn tiền
    REFUND_FAILED   // gọi hoàn tiền lỗi, cần admin xử lý tay
}
