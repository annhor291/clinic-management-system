package com.example.clinic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorResponse {
    private Long id;

    // Thông tin tài khoản
    private Long userId;
    private String username;
    private String email;

    // Thông tin chuyên khoa — trả tên thay vì id để client không cần query thêm
    private Long specialtyId;
    private String specialtyName;

    // Thông tin bác sĩ
    private String fullName;
    private String title;
    private Integer experienceYears;
    private String phone;
    private String bio;
    private BigDecimal consultationFee;
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
