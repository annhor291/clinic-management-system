package com.example.clinic.booking.engine;

import com.example.clinic.entity.enums.SlotStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// Toàn bộ thông tin cần thiết để 1 Booking Rule thực hiện validate.
// Bất biến (record) và KHÔNG phụ thuộc trực tiếp vào TimeSlot hay bất kỳ entity cụ thể nào
// — để sau này chuyển sang Dynamic Slot, các Rule không cần sửa lại.
public record BookingContext (

    Long doctorId,
    Long patientId,
    LocalDate appointmentDate,
    LocalTime startTime,
    LocalTime endTime,

    // dùng khi reschedule — loại trừ chính appointment đang đổi
    Long excludeAppointmentId,

    // thời điểm hiện tại — cho phép test rule với thời gian giả lập
    LocalDateTime now,

    SlotStatus slotStatus,
    boolean hasConflictingAppointment,
    boolean hasOverlappingAppointment,
    boolean hasBufferViolation
)

    {
        // Thời điểm bắt đầu đầy đủ (ngày + giờ)
        public LocalDateTime startDateTime() {
            return LocalDateTime.of(appointmentDate, startTime);
        }

        // Thời điểm kết thúc đầy đủ (ngày + giờ)
        public LocalDateTime endDateTime() {
            return LocalDateTime.of(appointmentDate, endTime);
        }
    }

