package com.example.clinic.mapper;

import com.example.clinic.dto.response.WorkScheduleAuditLogResponse;
import com.example.clinic.entity.WorkScheduleAuditLog;
import org.springframework.stereotype.Component;

@Component
public class WorkScheduleAuditLogMapper {

    public WorkScheduleAuditLogResponse toResponse(WorkScheduleAuditLog entity) {
        return WorkScheduleAuditLogResponse.builder()
                .id(entity.getId())
                .doctorId(entity.getDoctor().getId())
                .doctorName(entity.getDoctor().getFullName())
                .actionType(entity.getActionType())
                .performedById(entity.getPerformedBy().getId())
                .performedByName(entity.getPerformedBy().getUsername())
                .performedAt(entity.getPerformedAt())
                .details(entity.getDetails())
                .relatedEntityType(entity.getRelatedEntityType())
                .relatedEntityId(entity.getRelatedEntityId())
                .build();
    }
}
