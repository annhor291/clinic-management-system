package com.example.clinic.booking.event;

import java.time.LocalDateTime;

// Đổi lịch trực tiếp: vẫn là cùng một lịch hẹn, chỉ đổi slot (có thể đổi bác sĩ).
// feeChanged = true khi phí khám của bác sĩ mới khác bác sĩ cũ (giao dịch đang chờ thanh toán phải hủy).
public record AppointmentRescheduledEvent(Long appointmentId,
                                          LocalDateTime oldAppointmentTime,
                                          boolean feeChanged) implements AppointmentEvent {

}
