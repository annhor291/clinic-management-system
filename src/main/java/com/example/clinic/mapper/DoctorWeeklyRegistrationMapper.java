package com.example.clinic.mapper;

import com.example.clinic.dto.response.DayShiftResponse;
import com.example.clinic.dto.response.WeeklyRegistrationResponse;
import com.example.clinic.entity.DoctorWeeklyRegistration;
import com.example.clinic.entity.DoctorWeeklyRegistrationDay;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DoctorWeeklyRegistrationMapper {

    public WeeklyRegistrationResponse toResponse(DoctorWeeklyRegistration entity) {
        List<DayShiftResponse> days = entity.getDays().stream()
                .sorted(Comparator.comparingInt(d -> d.getDayOfWeek().getValue()))
                .map(this::toDayResponse)
                .toList();

        return WeeklyRegistrationResponse.builder()
                .id(entity.getId())
                .doctorId(entity.getDoctor().getId())
                .doctorName(entity.getDoctor().getFullName())
                .weekStartDate(entity.getWeekStartDate())
                .status(entity.getStatus())
                .submittedAt(entity.getSubmittedAt())
                .decidedById(entity.getDecidedBy() != null ? entity.getDecidedBy().getId() : null)
                .decidedByName(entity.getDecidedBy() != null ? entity.getDecidedBy().getUsername() : null)
                .decidedAt(entity.getDecidedAt())
                .rejectionReason(entity.getRejectionReason())
                .days(days)
                .build();
    }

    private DayShiftResponse toDayResponse(DoctorWeeklyRegistrationDay day) {
        return DayShiftResponse.builder()
                .dayOfWeek(day.getDayOfWeek())
                .shiftType(day.getShiftType())
                .build();
    }

}
