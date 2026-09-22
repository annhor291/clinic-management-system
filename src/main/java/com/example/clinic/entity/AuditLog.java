package com.example.clinic.entity;

import com.example.clinic.entity.enums.AuditActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

// Audit Log tổng quát toàn hệ thống — insert-only, chỉ ghi các hành động nhạy cảm/rủi ro cao
// đã liệt kê trong AuditActionType. Khác với WorkScheduleAuditLog (log riêng cho module lịch
// làm việc, bắt buộc gắn với 1 bác sĩ cụ thể) — bảng này dùng chung cho mọi loại entity.
// Ánh xạ đúng theo bảng audit_logs đã có sẵn từ đầu dự án — không tạo bảng mới.
@Entity
@Table(name = "audit_logs")
@Data
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    // Nullable ở DB — để dành cho hành động hệ thống tự động sau này (chưa dùng tới)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditActionType action;

    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
