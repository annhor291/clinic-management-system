package com.example.clinic.entity;

import com.example.clinic.entity.enums.WorkScheduleActionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "work_schedule_audit_logs",
        indexes = {
                @Index(name = "idx_worklog_doctor", columnList = "doctor_id"),
                @Index(name = "idx_worklog_performed_at", columnList = "performed_at")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkScheduleAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private WorkScheduleActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by", nullable = false)
    private User performedBy;

    @Column(name = "performed_at", updatable = false)
    private LocalDateTime performedAt;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "related_entity_type", length = 30)
    private String relatedEntityType;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @PrePersist
    protected void onCreate() {
        performedAt = LocalDateTime.now();
    }
}
