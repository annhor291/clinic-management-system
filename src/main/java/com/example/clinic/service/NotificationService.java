package com.example.clinic.service;

import com.example.clinic.entity.enums.Role;

import java.time.LocalDateTime;

public interface NotificationService {

    void notifyAppointmentBooked(Long appointmentId);

    void notifyAppointmentConfirmed(Long appointmentId);

    void notifyAppointmentCancelled(Long appointmentId);

    void notifyAppointmentCompleted(Long appointmentId);

    void notifyAppointmentNoShow(Long appointmentId);

    void notifyAppointmentExpired(Long appointmentId);

    void notifyAppointmentRescheduled(Long appointmentId, LocalDateTime oldAppointmentTime);

    void notifyAppointmentReminder24h(Long appointmentId);

    void notifyAppointmentReminder1h(Long appointmentId);

    void notifyPaymentSucceeded(Long paymentId);

    void notifyPaymentRefunded(Long paymentId);

    // Chào mừng người dùng mới (trong app + email)
    void notifyWelcome(Long userId);

    // Admin gửi thông báo chung trong app; targetRole = null là gửi cho tất cả. Trả về số người nhận
    int broadcastGeneral(String title, String content, Role targetRole);
}
