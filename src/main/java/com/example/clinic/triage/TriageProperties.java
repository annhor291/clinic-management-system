package com.example.clinic.triage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

// Cấu hình tính năng gợi ý chuyên khoa. Số cấp cứu chỉ khai báo ở ĐÂY (mặc định 115),
// muốn đổi chỉ cần thêm dòng triage.emergency-number=... vào application.properties.
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "triage")
public class TriageProperties {

    @NotBlank
    private String emergencyNumber = "113 (hoặc 115)";

    // Tìm lịch trống gần nhất của bác sĩ trong bao nhiêu ngày tới
    @Min(1)
    private int lookAheadDays = 14;
}
