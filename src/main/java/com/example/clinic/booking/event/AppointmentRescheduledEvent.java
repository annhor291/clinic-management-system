package com.example.clinic.booking.event;

// Sự kiện riêng cho reschedule — mang cả id lịch cũ và lịch mới.
// Cố tình KHÔNG bắn thêm AppointmentCancelledEvent + AppointmentBookedEvent khi reschedule,
// vì về nghiệp vụ đây là 1 hành động duy nhất ("đổi lịch"), không phải 2 hành động độc lập.
// Listener phía Notification sau này chỉ cần gửi 1 thông báo "Lịch của bạn đã được đổi",
// tránh gây hiểu nhầm nếu bắn riêng 2 thông báo huỷ + đặt mới.
public record AppointmentRescheduledEvent(Long oldAppointmentId,
                                          Long newAppointmentId) implements AppointmentEvent {

    @Override
    public Long appointmentId() {
        return newAppointmentId;
    }

}
