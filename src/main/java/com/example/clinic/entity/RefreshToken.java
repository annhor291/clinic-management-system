package com.example.clinic.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "refresh_tokens",
        indexes = {
                @Index(name = "idx_refresh_user_id", columnList = "user_id")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Token ngẫu nhiên dạng UUID, unique mỗi thiết bị
    @Column(nullable = false, unique = true)
    private String token;

    // Thời điểm token hết hạn
    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    // true = đã logout hoặc bị thu hồi
    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Nhiều refresh token thuộc về 1 user
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Kiểm tra token còn hạn và chưa bị thu hồi
    public boolean isValid() {
        return !revoked && LocalDateTime.now().isBefore(expiryDate);
    }
}
