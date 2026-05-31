package com.example.clinic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO nhận dữ liệu khi bệnh nhân đặt lịch khám
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentRequest {

    @NotNull(message = "ID bệnh nhân không được để trống")
    private Long patientId;

    @NotNull(message = "ID slot không được để trống")
    private Long timeSlotId;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
