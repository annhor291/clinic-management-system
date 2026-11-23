package com.example.clinic.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

// Thiếu cấu hình thì app báo lỗi lúc khởi động, tránh trả toạ độ sai cho bệnh nhân
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "clinic")
public class ClinicProperties {

    @NotBlank private String name;
    @NotBlank
    private String address;
    private String phone;
    private String email;
    private String openingHours;
    @NotNull @DecimalMin("-90") @DecimalMax("90") private Double latitude;
    @NotNull @DecimalMin("-180") @DecimalMax("180") private Double longitude;
}
