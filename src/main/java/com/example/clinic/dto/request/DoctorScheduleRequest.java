package com.example.clinic.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

// DTO nhận dữ liệu khi bác sĩ tạo ca làm việc
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorScheduleRequest {

    @NotNull(message = "ID bác sĩ không được để trống")
    private Long doctorId;

    @NotNull(message = "Ngày làm việc không được để trống")
    @FutureOrPresent(message = "Ngày làm việc phải là hôm nay hoặc trong tương lai")
    private LocalDate workDate;

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalTime endTime;

    // Thời gian mỗi slot (phút), mặc định 30 phút
    @Min(value = 15, message = "Thời gian mỗi slot tối thiểu 15 phút")
    @Max(value = 120, message = "Thời gian mỗi slot tối đa 120 phút")
    private Integer slotDurationMinutes = 30;

    private String note;
}
