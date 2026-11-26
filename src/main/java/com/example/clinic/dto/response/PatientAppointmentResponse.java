package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.entity.enums.CancelledBy;
import com.example.clinic.entity.enums.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Lịch hẹn nhìn từ phía bệnh nhân: có phiếu khám, thanh toán, thông báo trễ giờ
@Data
@Builder
public class PatientAppointmentResponse {

    private Long id;
    private String bookingCode;

    private Long patientId;
    private String patientName;

    private Long doctorId;
    private String doctorName;
    private String specialtyName;

    private Long timeSlotId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime appointmentTime;

    private AppointmentStatus status;
    private String note;

    private String cancellationReason;
    private LocalDateTime cancelledAt;
    private CancelledBy cancelledBy;
    private boolean cancelledByClinic;

    private Long rescheduledFromId;
    private String rescheduledFromBookingCode;

    private Integer queueNumber;
    private String estimatedTimeLabel;
    private String arrivalNote;
    private boolean delayed;
    private String delayMessage;

    private PaymentStatus paymentStatus;

    private LocalDateTime createdAt;
}
