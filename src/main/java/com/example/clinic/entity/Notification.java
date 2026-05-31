package com.example.clinic.entity;

import com.example.clinic.entity.enums.NotificationStatus;
import com.example.clinic.entity.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notif_user",    columnList = "user_id"),
                @Index(name = "idx_notif_status",  columnList = "status"),
                // Index để Scheduler quét mail chờ gửi
                @Index(name = "idx_notif_pending", columnList = "status, scheduled_at"),
                @Index(name = "idx_notif_read",    columnList = "user_id, is_read")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Nullable: không phải thông báo nào cũng liên quan đến lịch khám
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private boolean read = false;

    // Thời điểm dự định gửi (Scheduler dựa vào cột này)
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    // Thời điểm thực tế đã gửi thành công
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // Lưu lỗi nếu gửi thất bại → dễ debug
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
