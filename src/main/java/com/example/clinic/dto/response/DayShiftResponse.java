package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.ShiftType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayShiftResponse {

    private DayOfWeek dayOfWeek;
    private ShiftType shiftType;
}
