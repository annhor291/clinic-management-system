package com.example.clinic.booking.availability;

// Kết quả kiểm tra lịch hẹn hiện có của bệnh nhân so với 1 khung giờ mới
public record PatientScheduleCheckResult (

        boolean hasOverlap,
        boolean hasBufferViolation
) {
}


