package com.example.clinic.booking.event;

// Interface đánh dấu chung cho mọi domain event liên quan Appointment.
// Giúp phase Notification/Audit Log sau này có thể viết 1 listener tổng quát
// (VD: @EventListener(AppointmentEvent.class)) nếu cần log mọi loại sự kiện,
// thay vì phải liệt kê từng loại event riêng lẻ.
public interface AppointmentEvent {

    Long appointmentId();
}
