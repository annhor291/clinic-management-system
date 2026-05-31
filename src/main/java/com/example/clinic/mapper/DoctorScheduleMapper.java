package com.example.clinic.mapper;

import com.example.clinic.dto.request.DoctorScheduleRequest;
import com.example.clinic.dto.response.DoctorScheduleResponse;
import com.example.clinic.dto.response.TimeSlotResponse;
import com.example.clinic.entity.DoctorSchedule;
import com.example.clinic.entity.enums.SlotStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class DoctorScheduleMapper {

    private final TimeSlotMapper timeSlotMapper;

    // Convert DoctorScheduleRequest → DoctorSchedule entity
    // Không gán doctor vì Service sẽ gán sau
    public DoctorSchedule toEntity(DoctorScheduleRequest request) {
        return DoctorSchedule.builder()
                .workDate(request.getWorkDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .slotDurationMinutes(request.getSlotDurationMinutes())
                .note(request.getNote())
                .active(true)
                .build();
    }

    // Convert DoctorSchedule entity → DoctorScheduleResponse (không kèm slot)
    public DoctorScheduleResponse toResponse(DoctorSchedule schedule) {
        return buildResponse(schedule, Collections.emptyList());
    }

    // Convert DoctorSchedule entity → DoctorScheduleResponse kèm danh sách slot
    public DoctorScheduleResponse toResponseWithSlots(DoctorSchedule schedule) {
        List<TimeSlotResponse> slotResponses = schedule.getTimeSlots()
                .stream()
                .map(timeSlotMapper::toResponse)
                .collect(Collectors.toList());
        return buildResponse(schedule, slotResponses);
    }

    // ===== Private helper =====
    private DoctorScheduleResponse buildResponse(
            DoctorSchedule schedule, List<TimeSlotResponse> slotResponses) {

        // Đếm số slot available từ danh sách slot
        long availableSlots = slotResponses.stream()
                .filter(s -> s.getStatus() == SlotStatus.AVAILABLE)
                .count();

        return DoctorScheduleResponse.builder()
                .id(schedule.getId())
                .doctorId(schedule.getDoctor().getId())
                .doctorName(schedule.getDoctor().getFullName())
                .workDate(schedule.getWorkDate())
                .startTime(schedule.getStartTime())
                .endTime(schedule.getEndTime())
                .slotDurationMinutes(schedule.getSlotDurationMinutes())
                .active(schedule.isActive())
                .note(schedule.getNote())
                .totalSlots(slotResponses.size())
                .availableSlots((int) availableSlots)
                .timeSlots(slotResponses)
                .createdAt(schedule.getCreatedAt())
                .build();
    }
}
