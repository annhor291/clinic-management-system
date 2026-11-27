package com.example.clinic.entity.enums;

public enum AppointmentStatus {
    PENDING,    // Chờ xác nhận (thanh toán hoặc lễ tân/quản trị viên xác nhận)
    CONFIRMED,  // Bác sĩ đã xác nhận
    COMPLETED,  // Đã khám xong
    CANCELLED,  // Đã huỷ
    NO_SHOW,     // Bệnh nhân không đến
    EXPIRED     // Hết hạn xác nhận — do Scheduler tự động chuyển khi quá thời gian giữ PENDING
}
