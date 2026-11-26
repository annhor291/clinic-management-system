package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.entity.enums.CancelledBy;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Lịch hẹn nhìn từ phía bác sĩ: chỉ thông tin phục vụ khám,
// không có thanh toán và các câu thông báo dành riêng cho bệnh nhân
@Data
@Builder
public class DoctorAppointmentResponse {

    private Long id;
    private String bookingCode;

    private Long patientId;
    private String patientName;
    private String patientPhone;

    private Long doctorId;
    private String specialtyName;

    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime appointmentTime;
    private Integer queueNumber;

    private AppointmentStatus status;
    private String note;

    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private CancelledBy cancelledBy;
    private boolean cancelledByClinic;

    private boolean delayed;
}
