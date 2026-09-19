package com.example.clinic.entity.enums;

public enum ShiftType {
    NONE,       // Không làm việc ngày đó — chỉ dùng trong đăng ký tuần, không sinh thành DoctorSchedule
    MORNING,    // Ca sáng cố định 08:00 - 12:00
    AFTERNOON,  // Ca chiều cố định 13:00 - 17:00
    FULL,       // Cả 2 ca — hệ thống tách thành 2 DoctorSchedule (MORNING + AFTERNOON) khi sinh lịch
    CUSTOM      // Ca giờ tuỳ chỉnh do Admin tạo thủ công qua API cũ (POST /doctor-schedules)
}
