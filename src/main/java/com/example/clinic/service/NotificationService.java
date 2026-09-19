package com.example.clinic.service;

public interface NotificationService {

    void notifyAppointmentBooked(Long appointmentId);

    void notifyAppointmentConfirmed(Long appointmentId);

    void notifyAppointmentCancelled(Long appointmentId);

    void notifyAppointmentCompleted(Long appointmentId);

    void notifyAppointmentNoShow(Long appointmentId);

    void notifyAppointmentExpired(Long appointmentId);

    void notifyAppointmentRescheduled(Long oldAppointmentId, Long newAppointmentId);

    void notifyAppointmentReminder24h(Long appointmentId);

    void notifyAppointmentReminder1h(Long appointmentId);
}
