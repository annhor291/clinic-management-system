package com.example.clinic.booking.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

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

    // Thời gian tối đa giữ trạng thái PENDING trước khi tự động chuyển EXPIRED (phút)
    private int pendingExpiryMinutes = 30;
}
