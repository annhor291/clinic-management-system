package com.example.clinic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO nhận dữ liệu khi đổi lịch khám
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleRequest {

    // ID slot mới muốn đổi sang
    @NotNull(message = "ID slot mới không được để trống")
    private Long newTimeSlotId;

    @Size(max = 500, message = "Lý do đổi lịch không được vượt quá 500 ký tự")
    private String reason;
}
