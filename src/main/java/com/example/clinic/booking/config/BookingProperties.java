package com.example.clinic.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
@ConfigurationProperties(prefix = "booking")
@Data
public class BookingProperties {

    // Thời gian tối thiểu trước giờ hẹn để được phép đặt lịch (phút)
    private int minLeadTimeMinutes = 30;

    // Thời gian tối thiểu trước giờ hẹn để được phép hủy lịch (giờ)
    private int cancelWindowHours = 2;

    // Thời gian tối thiểu trước giờ hẹn để được phép đổi lịch (giờ)
    private int rescheduleWindowHours = 2;

    // Khoảng đệm tối thiểu giữa 2 lịch hẹn của cùng 1 bệnh nhân (phút)
    private int bufferMinutes = 15;

    // Giới hạn số hồ sơ người thân (không có tài khoản riêng) mỗi tài khoản được quản lý —
    // chống dữ liệu rác (spam tạo hồ sơ ảo)
    private int maxManagedPatients = 10;

    // Gợi ý lịch thay thế khi lịch bị hủy
    private int suggestionWindowDays = 14;
    private int suggestionMaxResults = 5;

    // Hạn xác nhận lịch PENDING = sớm hơn của (giờ đặt + confirmMaxHours) và (giờ hẹn − confirmBeforeStartMinutes)
    private int confirmMaxHours = 24;
    private int confirmBeforeStartMinutes = 15;

    public LocalDateTime confirmDeadline(LocalDateTime createdAt, LocalDateTime appointmentTime) {
        LocalDateTime byAge = createdAt.plusHours(confirmMaxHours);
        LocalDateTime byStart = appointmentTime.minusMinutes(confirmBeforeStartMinutes);
        return byAge.isBefore(byStart) ? byAge : byStart;
    }
}
