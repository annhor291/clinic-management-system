package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

// DTO trả về cho client — không lộ thông tin nhạy cảm của Entity
// VD: không trả password, không trả các field nội bộ
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PatientResponse {
    private Long id;

    // Thông tin tài khoản
    private Long userId;
    private String username;
    private String email;

    // Thông tin cá nhân
    private String fullName;
    private String phone;
    private Gender gender;
    private LocalDate dateOfBirth;
    private String address;

    // Thông tin y tế
    private String bloodType;
    private String allergies;
    private String medicalNotes;
    private String insuranceNumber;

    // Người liên hệ khẩn cấp
    private String emergencyContactName;
    private String emergencyContactPhone;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
