package com.example.clinic.mapper;

import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.enums.AppointmentStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AppointmentMapper {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    // Convert Appointment entity → AppointmentResponse
    public AppointmentResponse toResponse(Appointment appointment) {
        AppointmentResponse.AppointmentResponseBuilder builder = AppointmentResponse.builder()
                .id(appointment.getId())
                .bookingCode(appointment.getBookingCode())
                // Thông tin bệnh nhân
                .patientId(appointment.getPatient().getId())
                .patientName(appointment.getPatient().getFullName())
                .patientPhone(appointment.getPatient().getPhone())
                // Thông tin bác sĩ
                .doctorId(appointment.getDoctor().getId())
                .doctorName(appointment.getDoctor().getFullName())
                .specialtyName(appointment.getDoctor().getSpecialty().getName())
                // Thông tin slot
                .timeSlotId(appointment.getTimeSlot().getId())
                .appointmentDate(appointment.getTimeSlot().getSlotDate())
                .startTime(appointment.getTimeSlot().getStartTime())
                .endTime(appointment.getTimeSlot().getEndTime())
                .appointmentTime(appointment.getAppointmentTime())
                // Trạng thái
                .status(appointment.getStatus())
                .note(appointment.getNote())
                // Thông tin hủy lịch
                .cancellationReason(appointment.getCancellationReason())
                .cancelledAt(appointment.getCancelledAt())
                .cancelledBy(appointment.getCancelledBy())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                // Phiếu khám điện tử
                .queueNumber(appointment.getTimeSlot().getQueueNumber())
                .estimatedTimeLabel(buildEstimatedTimeLabel(appointment))
                .arrivalNote("Vui lòng đến trước giờ hẹn 15-20 phút để làm thủ tục tiếp nhận.")
                .delayed(isDelayed(appointment))
                .delayMessage(buildDelayMessage(appointment))
                .scheduleId(appointment.getTimeSlot().getSchedule().getId());

        // Nếu là lịch đổi → lấy thêm thông tin lịch cũ
        if (appointment.getRescheduledFrom() != null) {
            builder.rescheduledFromId(appointment.getRescheduledFrom().getId())
                    .rescheduledFromBookingCode(appointment.getRescheduledFrom().getBookingCode());
        }

        return builder.build();
    }

    // Câu chữ chuẩn — khung giờ chỉ mang tính dự kiến, không cam kết tuyệt đối,
    // đúng cách BookingCare/Medpro đang trình bày với bệnh nhân ngoài thực tế
    private String buildEstimatedTimeLabel(Appointment appointment) {
        String start = appointment.getTimeSlot().getStartTime().format(TIME_FORMATTER);
        String end = appointment.getTimeSlot().getEndTime().format(TIME_FORMATTER);
        return start + " - " + end + " (giờ dự kiến, có thể thay đổi theo tình hình thực tế tại phòng khám)";
    }

    // Trễ giờ: còn PENDING/CONFIRMED (chưa khám xong) nhưng giờ hiện tại đã qua giờ bắt đầu dự kiến
    private boolean isDelayed(Appointment appointment) {
        boolean stillWaiting = appointment.getStatus() == AppointmentStatus.PENDING
                || appointment.getStatus() == AppointmentStatus.CONFIRMED;
        if (!stillWaiting) return false;

        LocalDateTime slotStart = LocalDateTime.of(
                appointment.getTimeSlot().getSlotDate(), appointment.getTimeSlot().getStartTime());
        return LocalDateTime.now().isAfter(slotStart);
    }

    private String buildDelayMessage(Appointment appointment) {
        if (!isDelayed(appointment)) return null;
        return "Lịch hẹn đang bị trễ so với giờ dự kiến do các lượt khám trước phát sinh thêm thời gian. " +
                "Vui lòng kiên nhẫn chờ, phòng khám sẽ gọi đến lượt bạn sớm nhất có thể.";
    }
}
