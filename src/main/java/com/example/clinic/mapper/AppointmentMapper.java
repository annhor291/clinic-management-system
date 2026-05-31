package com.example.clinic.mapper;

import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.entity.Appointment;
import org.springframework.stereotype.Component;

@Component
public class AppointmentMapper {

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
                .updatedAt(appointment.getUpdatedAt());

        // Nếu là lịch đổi → lấy thêm thông tin lịch cũ
        if (appointment.getRescheduledFrom() != null) {
            builder.rescheduledFromId(appointment.getRescheduledFrom().getId())
                    .rescheduledFromBookingCode(appointment.getRescheduledFrom().getBookingCode());
        }

        return builder.build();
    }
}
