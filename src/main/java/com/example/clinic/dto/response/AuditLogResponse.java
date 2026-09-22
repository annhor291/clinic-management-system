package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.AuditActionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private String username;
    private AuditActionType action;
    private String entityType;
    private Long entityId;
    private String oldValue;
    private String newValue;
    private String description;
    private LocalDateTime createdAt;
}
