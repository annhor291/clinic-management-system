package com.example.clinic.entity.enums;

public enum NotificationStatus {
    PENDING,   // Chờ gửi
    SENT,      // Đã gửi thành công
    FAILED     // Gửi thất bại (xem error_message)
}
