package com.example.clinic.entity.enums;

public enum AppointmentStatus {
    PENDING,    // Chờ bác sĩ xác nhận
    CONFIRMED,  // Bác sĩ đã xác nhận
    COMPLETED,  // Đã khám xong
    CANCELLED,  // Đã huỷ
    NO_SHOW     // Bệnh nhân không đến
}
