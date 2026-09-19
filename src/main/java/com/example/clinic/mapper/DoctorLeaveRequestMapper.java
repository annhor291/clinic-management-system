package com.example.clinic.mapper;

import com.example.clinic.dto.response.LeaveRequestResponse;
import com.example.clinic.entity.DoctorLeaveRequest;
import org.springframework.stereotype.Component;

@Component
public class DoctorLeaveRequestMapper {

    public LeaveRequestResponse toResponse(DoctorLeaveRequest entity) {
        return LeaveRequestResponse.builder()
                .id(entity.getId())
                .doctorId(entity.getDoctor().getId())
                .doctorName(entity.getDoctor().getFullName())
                .type(entity.getType())
                .startDate(entity.getStartDate())
                .endDate(entity.getEndDate())
                .reason(entity.getReason())
                .status(entity.getStatus())
                .submittedAt(entity.getSubmittedAt())
                .decidedById(entity.getDecidedBy() != null ? entity.getDecidedBy().getId() : null)
                .decidedByName(entity.getDecidedBy() != null ? entity.getDecidedBy().getUsername() : null)
                .decidedAt(entity.getDecidedAt())
                .rejectionReason(entity.getRejectionReason())
                .build();
    }
}
