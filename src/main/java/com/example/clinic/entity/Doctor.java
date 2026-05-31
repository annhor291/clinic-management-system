package com.example.clinic.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "doctors",
        indexes = {
                @Index(name = "idx_doctors_specialty", columnList = "specialty_id"),
                @Index(name = "idx_doctors_active",    columnList = "active"),
                @Index(name = "idx_doctors_fullname",  columnList = "full_name")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "specialty_id", nullable = false)
    private Specialty specialty;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "title", length = 50)
    private String title;                       // BS, ThS, TS, PGS, GS

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "bio", columnDefinition = "TEXT")
    private String bio;

    @Column(name = "consultation_fee", precision = 12, scale = 0)
    @Builder.Default
    private BigDecimal consultationFee = BigDecimal.ZERO;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Relationships =====
    @OneToMany(mappedBy = "doctor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DoctorSchedule> schedules;

    @OneToMany(mappedBy = "doctor", fetch = FetchType.LAZY)
    private List<Appointment> appointments;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
