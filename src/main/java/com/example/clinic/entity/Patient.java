package com.example.clinic.entity;

import com.example.clinic.entity.enums.Gender;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "patients",
        indexes = {
        @Index(name = "idx_patients_fullname", columnList = "full_name"),
        @Index(name = "idx_patients_phone",    columnList = "phone")
        }
)
@Data
@ToString(exclude = {"user", "appointments"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    // Tài khoản quản lý hồ sơ này — CHỈ có giá trị khi user = null (hồ sơ người thân, không tự đăng nhập)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "managed_by_user_id")
    private User managedBy;

    // Email riêng của người thân để nhận thông báo — không dùng để đăng nhập, chỉ để gửi mail
    @Column(name = "contact_email", length = 255)
    private String contactEmail;

    // Hiển thị UI — VD "Mẹ", "Con", "Bản thân"
    @Column(name = "relationship_label", length = 50)
    private String relationshipLabel;

    // ===== Thông tin cá nhân =====
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 10)
    private Gender gender;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "address", length = 255)
    private String address;

    // ===== Thông tin y tế =====
    @Column(name = "blood_type", length = 5)
    private String bloodType;                   // A, B, AB, O (+/-)

    @Column(name = "allergies", columnDefinition = "TEXT")
    private String allergies;                   // dị ứng thuốc, thực phẩm

    @Column(name = "medical_notes", columnDefinition = "TEXT")
    private String medicalNotes;                // tiền sử bệnh, bệnh nền

    @Column(name = "insurance_number", unique = true, length = 20)
    private String insuranceNumber;             // số thẻ BHYT

    // ===== Người liên hệ khẩn cấp =====
    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    // ===== Thời gian tạo và cập nhật =====
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Relationships =====
    @OneToMany(mappedBy = "patient", fetch = FetchType.LAZY)
    private List<Appointment> appointments;

    // Kiểm tra 1 userId có quyền sở hữu/quản lý hồ sơ bệnh nhân này không —
    // dùng chung cho mọi ownership check trong AppointmentServiceImpl, thay thế cách gọi
    // patient.getUser().getId().equals(...) trực tiếp (sẽ NPE với hồ sơ người thân không có user).
    public boolean isOwnedByUser(Long userId) {
        if (userId == null) return false;
        if (user != null) return user.getId().equals(userId);
        return managedBy != null && managedBy.getId().equals(userId);
    }

    // Email dùng để gửi thông báo — ưu tiên email tài khoản chính chủ, nếu không có (hồ sơ người thân)
    // thì dùng contactEmail riêng nếu được nhập, cuối cùng fallback về null (không gửi được)
    public String getNotificationEmail() {
        if (user != null) return user.getEmail();
        return contactEmail;
    }

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

}
