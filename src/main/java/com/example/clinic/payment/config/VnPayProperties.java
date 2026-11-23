package com.example.clinic.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Data
public class VnPayProperties {

    private String tmnCode;
    private String hashSecret;
    private String payUrl;
    private String apiUrl;
    private String returnUrl;

    // Thời gian sống của 1 giao dịch (phút)
    private int paymentExpireMinutes = 15;

    // IP máy chủ gửi kèm API hoàn tiền (VNPay bắt buộc có vnp_IpAddr)
    private String refundIp = "127.0.0.1";
}
