package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.SlotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

// DTO trả về thông tin 1 slot giờ
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlotResponse {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private Long scheduleId;

    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;

    // AVAILABLE, BOOKED, BLOCKED, COMPLETED, CANCELLED
    private SlotStatus status;
}
