package com.example.clinic.booking.availability;

import com.example.clinic.booking.config.BookingProperties;
import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.repository.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AppointmentPatientAvailabilityGateway implements PatientAvailabilityGateway {

    private final AppointmentRepository appointmentRepository;
    private final BookingProperties bookingProperties;

    @Override
    public PatientScheduleCheckResult check(
            Long patientId, LocalDate date, LocalTime start, LocalTime end, Long excludeAppointmentId) {

        List<Appointment> sameDayActive = appointmentRepository.findActiveAppointmentsForPatientOnDate(
                patientId,
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
                date,
                excludeAppointmentId
        );

        int bufferMinutes = bookingProperties.getBufferMinutes();

        boolean hasOverlap = sameDayActive.stream().anyMatch(a -> overlaps(
                start, end, a.getTimeSlot().getStartTime(), a.getTimeSlot().getEndTime()));

        boolean hasBufferViolation = sameDayActive.stream().anyMatch(a -> tooClose(
                start, end, a.getTimeSlot().getStartTime(), a.getTimeSlot().getEndTime(), bufferMinutes));

        return new PatientScheduleCheckResult(hasOverlap, hasBufferViolation);
    }

    // Trùng giờ: newStart < oldEnd AND newEnd > oldStart (đúng công thức bạn yêu cầu ban đầu)
    private boolean overlaps(LocalTime newStart, LocalTime newEnd, LocalTime oldStart, LocalTime oldEnd) {
        return newStart.isBefore(oldEnd) && newEnd.isAfter(oldStart);
    }

    // Quá gần: không trùng nhau nhưng khoảng cách giữa 2 lịch nhỏ hơn buffer cho phép
    private boolean tooClose(LocalTime newStart, LocalTime newEnd, LocalTime oldStart, LocalTime oldEnd, int bufferMinutes) {
        if (overlaps(newStart, newEnd, oldStart, oldEnd)) {
            return false; // đã được PatientOverlapRule bắt riêng, không tính trùng làm vi phạm buffer
        }
        long gap = !newEnd.isAfter(oldStart)
                ? Duration.between(newEnd, oldStart).toMinutes()   // lịch mới kết thúc trước, lịch cũ bắt đầu sau
                : Duration.between(oldEnd, newStart).toMinutes();  // lịch mới bắt đầu sau khi lịch cũ đã kết thúc
        return gap < bufferMinutes;
    }
}
