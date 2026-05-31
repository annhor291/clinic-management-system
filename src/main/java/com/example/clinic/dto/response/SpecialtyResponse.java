package com.example.clinic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// DTO trả về cho client
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SpecialtyResponse {

    private Long id;
    private String name;
    private String description;
    private boolean active;

    // Số lượng bác sĩ thuộc chuyên khoa này — tính toán thêm, không có trong Entity
    private Integer totalDoctors;

    private LocalDateTime createdAt;
}
