package com.example.clinic.entity;

import com.example.clinic.entity.enums.RegistrationStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "doctor_weekly_registrations",
        indexes = {
                @Index(name = "idx_registration_doctor_week", columnList = "doctor_id, week_start_date")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_registration_doctor_week", columnNames = {"doctor_id", "week_start_date"})
        }
)
@Data
@ToString(exclude = {"doctor", "decidedBy", "days"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorWeeklyRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private User decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @OneToMany(mappedBy = "registration", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<DoctorWeeklyRegistrationDay> days;

    @PrePersist
    protected void onCreate() {
        submittedAt = LocalDateTime.now();
    }
}
