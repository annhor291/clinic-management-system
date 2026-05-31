package com.example.clinic.entity.enums;

public enum SlotStatus {
    AVAILABLE,  // Còn trống → bệnh nhân có thể đặt
    BOOKED,     // Đã có người đặt
    BLOCKED,    // Bác sĩ khoá (nghỉ đột xuất...)
    COMPLETED,  // Đã khám xong
    CANCELLED   // Lịch bị huỷ → slot trống trở lại
}
