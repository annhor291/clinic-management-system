package com.example.clinic.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyRegistrationRequest {

    // Bắt buộc là ngày Thứ Hai — mốc xác định tuần đăng ký
    @NotNull(message = "Ngày bắt đầu tuần không được để trống")
    private LocalDate weekStartDate;

    // T2-T6 bắt buộc có mặt (shiftType != NONE/CUSTOM), T7/CN tuỳ chọn — không muốn làm thì bỏ qua, không gửi lên
    @NotEmpty(message = "Phải chọn ít nhất các ngày làm việc trong tuần")
    @Valid
    private List<DayShiftRequest> days;
}
