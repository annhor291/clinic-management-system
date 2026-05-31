package com.example.clinic.entity;

import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.entity.enums.CancelledBy;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(
        name = "appointments",
        indexes = {
                @Index(name = "idx_appt_patient",      columnList = "patient_id"),
                @Index(name = "idx_appt_doctor_time",  columnList = "doctor_id, appointment_time"),
                @Index(name = "idx_appt_status",       columnList = "status"),
                // Index phục vụ Scheduler nhắc lịch
                @Index(name = "idx_appt_reminder",
                        columnList = "appointment_time, status, reminder_24h_sent, reminder_1h_sent")
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mã hiển thị cho người dùng. VD: APT-20260516-0001
    @Column(name = "booking_code", nullable = false, unique = true, length = 30)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    // 1 slot chỉ được đặt bởi 1 appointment
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "time_slot_id", nullable = false, unique = true)
    private TimeSlot timeSlot;

    @Column(name = "appointment_time", nullable = false)
    private LocalDateTime appointmentTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;                        // ghi chú của bệnh nhân

    // ===== Huỷ lịch =====
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 10)
    private CancelledBy cancelledBy;

    // ===== Đổi lịch =====
    // Tự tham chiếu: nếu đây là lịch đổi → trỏ về lịch cũ
    // VD: appointment(id=2).rescheduledFrom = appointment(id=1)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rescheduled_from_id")
    private Appointment rescheduledFrom;

    // ===== Scheduler nhắc lịch =====
    // Đánh dấu đã gửi email nhắc chưa → tránh gửi lặp
    @Column(name = "reminder_24h_sent", nullable = false)
    @Builder.Default
    private boolean reminder24hSent = false;

    @Column(name = "reminder_1h_sent", nullable = false)
    @Builder.Default
    private boolean reminder1hSent = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ===== Relationships =====
    @OneToMany(mappedBy = "appointment", fetch = FetchType.LAZY)
    private List<Notification> notifications;

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
