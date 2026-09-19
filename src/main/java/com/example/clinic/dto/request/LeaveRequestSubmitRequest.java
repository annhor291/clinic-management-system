package com.example.clinic.dto.request;

import com.example.clinic.entity.enums.LeaveType;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveRequestSubmitRequest {

    @NotNull(message = "Loại nghỉ không được để trống")
    private LeaveType type;

    @NotNull(message = "Ngày bắt đầu nghỉ không được để trống")
    @FutureOrPresent(message = "Không thể xin nghỉ cho ngày đã qua")
    private LocalDate startDate;

    @NotNull(message = "Ngày kết thúc nghỉ không được để trống")
    private LocalDate endDate;

    @NotBlank(message = "Lý do xin nghỉ không được để trống")
    private String reason;
}
