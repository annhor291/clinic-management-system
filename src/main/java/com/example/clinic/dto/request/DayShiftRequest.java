package com.example.clinic.dto.request;

import com.example.clinic.entity.enums.ShiftType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayShiftRequest {

    @NotNull(message = "Ngày trong tuần không được để trống")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "Ca làm việc không được để trống")
    private ShiftType shiftType;
}
