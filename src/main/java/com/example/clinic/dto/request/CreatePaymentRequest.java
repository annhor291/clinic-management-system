package com.example.clinic.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {

    @NotNull(message = "Mã lịch hẹn không được để trống")
    private Long appointmentId;

    // Mặc định VNPAY nếu không gửi — để sẵn chỗ cho cổng khác (Momo) sau này
    private String gateway;
}
