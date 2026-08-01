package com.example.clinic.booking.availability;

import com.example.clinic.entity.TimeSlot;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

// Implementation dựa trên TimeSlot cố định (30 phút, sinh sẵn từ DoctorSchedule).
// Khi chuyển sang Dynamic Slot, thay thế bằng 1 implementation khác của DoctorAvailabilityGateway,
// không cần sửa BookingService hay bất kỳ BookingRule nào.
@Component
@RequiredArgsConstructor
public class TimeSlotAvailabilityGateway implements DoctorAvailabilityGateway {

    private final TimeSlotRepository timeSlotRepository;
    private final AppointmentRepository appointmentRepository;

    @Override
    public TimeSlot lockSlot(Long timeSlotId) {
        return timeSlotRepository.findByIdWithLock(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy slot với id: " + timeSlotId));
    }

    @Override
    public boolean hasConflictingAppointment(Long timeSlotId) {
        return appointmentRepository.existsByTimeSlotIdAndStatusNotIn(
                timeSlotId, List.of(AppointmentStatus.CANCELLED, AppointmentStatus.EXPIRED));
    }
}
