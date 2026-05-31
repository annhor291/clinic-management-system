package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.entity.enums.CancelledBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// DTO trả về thông tin lịch hẹn
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentResponse {

    private Long id;

    // Mã đặt lịch hiển thị cho người dùng
    private String bookingCode;

    // Thông tin bệnh nhân
    private Long patientId;
    private String patientName;
    private String patientPhone;

    // Thông tin bác sĩ
    private Long doctorId;
    private String doctorName;
    private String specialtyName;

    // Thông tin slot
    private Long timeSlotId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime appointmentTime;

    // Trạng thái lịch hẹn
    private AppointmentStatus status;
    private String note;

    // Thông tin hủy lịch
    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private CancelledBy cancelledBy;

    // Lịch đổi từ appointment nào (nếu là lịch đổi)
    private Long rescheduledFromId;
    private String rescheduledFromBookingCode;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
