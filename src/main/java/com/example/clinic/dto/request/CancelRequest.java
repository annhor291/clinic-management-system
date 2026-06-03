package com.example.clinic.dto.request;

import com.example.clinic.entity.enums.CancelledBy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO nhận dữ liệu khi hủy lịch
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelRequest {

    @NotBlank(message = "Lý do hủy không được để trống")
    @Size(max = 500, message = "Lý do hủy không được vượt quá 500 ký tự")
    private String reason;
}
