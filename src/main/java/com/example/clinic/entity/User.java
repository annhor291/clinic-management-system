package com.example.clinic.entity;

import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;


@Entity
@Table(name = "users",
        indexes = {
                @Index(name = "idx_users_role", columnList = "role")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuthProvider provider;

    // Reset password token
    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Relationships =====

    // 1 user có tối đa 1 hồ sơ bệnh nhân (nếu role = PATIENT)
    // cascade ALL: xoá user → xoá patient theo
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Patient patient;

    // 1 user có tối đa 1 hồ sơ bác sĩ (nếu role = DOCTOR)
    // cascade ALL: xoá user → xoá doctor theo
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Doctor doctor;

    // 1 user có nhiều thông báo
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Notification> notifications;

    // Tự động gán createdAt và updatedAt ngay trước khi INSERT vào database
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // Tự động cập nhật updatedAt ngay trước khi UPDATE vào database
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== UserDetails =====
    // Spring Security gọi các method dưới đây để kiểm tra quyền và trạng thái tài khoản

    // Trả về danh sách quyền của user, dùng để phân quyền trong Spring Security
    // VD: role = ADMIN → authority = "ROLE_ADMIN"
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    // Dùng email làm định danh đăng nhập thay vì username
    @Override
    public String getUsername() { return email; }

    // Tài khoản không bao giờ hết hạn
    @Override
    public boolean isAccountNonExpired() { return true; }

    // Tài khoản không bị khoá theo cơ chế Spring (dùng enabled thay thế)
    @Override
    public boolean isAccountNonLocked() { return true; }

    // Mật khẩu không bao giờ hết hạn
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    // Trả về trạng thái enabled: false → Spring Security từ chối đăng nhập
    @Override
    public boolean isEnabled() { return enabled; }


}
