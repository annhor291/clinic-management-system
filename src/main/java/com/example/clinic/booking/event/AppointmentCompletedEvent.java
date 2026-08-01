package com.example.clinic.booking.event;

public record AppointmentCompletedEvent(Long appointmentId) implements AppointmentEvent {

}
