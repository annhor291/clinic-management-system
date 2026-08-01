package com.example.clinic.booking.event;

public record AppointmentExpiredEvent(Long appointmentId) implements AppointmentEvent {

}
