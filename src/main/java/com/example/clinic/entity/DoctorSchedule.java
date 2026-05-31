package com.example.clinic.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(
        name = "doctor_schedules",
        indexes = {
                @Index(name = "idx_schedule_doctor_date", columnList = "doctor_id, work_date"),
                @Index(name = "idx_schedule_work_date",   columnList = "work_date")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    // Thời gian mỗi slot (phút). VD: 30 → mỗi slot 30 phút
    @Column(name = "slot_duration_minutes", nullable = false)
    @Builder.Default
    private Integer slotDurationMinutes = 30;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ===== Relationships =====
    // Khi tạo schedule → service sẽ tự sinh các TimeSlot tương ứng
    @OneToMany(mappedBy = "schedule", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<TimeSlot> timeSlots;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
