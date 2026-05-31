package com.example.clinic.entity.enums;

public enum NotificationType {
    APPOINTMENT_BOOKED,       // Đặt lịch thành công
    APPOINTMENT_CONFIRMED,    // Bác sĩ xác nhận
    APPOINTMENT_CANCELLED,    // Lịch bị huỷ
    APPOINTMENT_COMPLETED,    // Khám xong
    APPOINTMENT_REMINDER_24H, // Nhắc trước 24 giờ (Scheduler)
    APPOINTMENT_REMINDER_1H,  // Nhắc trước 1 giờ  (Scheduler)
    WELCOME,                  // Chào mừng đăng ký
    GENERAL                   // Thông báo chung từ Admin
}
