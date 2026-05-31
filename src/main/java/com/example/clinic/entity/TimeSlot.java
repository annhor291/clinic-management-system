package com.example.clinic.entity;

import com.example.clinic.entity.enums.SlotStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(
        name = "time_slots",
        indexes = {
                @Index(name = "idx_slot_doctor_date",        columnList = "doctor_id, slot_date"),
                @Index(name = "idx_slot_doctor_date_status", columnList = "doctor_id, slot_date, status"),
                @Index(name = "idx_slot_schedule",           columnList = "schedule_id")
        },
        uniqueConstraints = {
                // Chống double-booking: 1 bác sĩ không thể có 2 slot cùng giờ cùng ngày
                @UniqueConstraint(
                        name = "uk_slot_doctor_date_time",
                        columnNames = {"doctor_id", "slot_date", "start_time"}
                )
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private DoctorSchedule schedule;

    // Lưu thêm doctor_id để query check lịch trống nhanh hơn
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SlotStatus status = SlotStatus.AVAILABLE;

    // Optimistic Locking: tránh 2 người cùng đặt 1 slot cùng lúc
    // Khi 2 transaction cùng update → người sau bị OptimisticLockException
    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Relationships =====
    // 1 slot chỉ có tối đa 1 appointment
    @OneToOne(mappedBy = "timeSlot", fetch = FetchType.LAZY)
    private Appointment appointment;

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
