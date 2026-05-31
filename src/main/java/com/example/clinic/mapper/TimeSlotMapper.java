package com.example.clinic.mapper;

import com.example.clinic.dto.response.TimeSlotResponse;
import com.example.clinic.entity.TimeSlot;
import org.springframework.stereotype.Component;

@Component
public class TimeSlotMapper {
    // Convert TimeSlot entity → TimeSlotResponse
    public TimeSlotResponse toResponse(TimeSlot slot) {
        return TimeSlotResponse.builder()
                .id(slot.getId())
                .doctorId(slot.getDoctor().getId())
                .doctorName(slot.getDoctor().getFullName())
                .scheduleId(slot.getSchedule().getId())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .status(slot.getStatus())
                .build();
    }
}
