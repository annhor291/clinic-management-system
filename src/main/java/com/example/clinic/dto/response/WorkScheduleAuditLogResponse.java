package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.WorkScheduleActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkScheduleAuditLogResponse {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private WorkScheduleActionType actionType;
    private Long performedById;
    private String performedByName;
    private LocalDateTime performedAt;
    private String details;
    private String relatedEntityType;
    private Long relatedEntityId;
}
