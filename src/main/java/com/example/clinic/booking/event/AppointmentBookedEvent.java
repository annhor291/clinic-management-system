package com.example.clinic.booking.event;

public record AppointmentBookedEvent(Long appointmentId) implements AppointmentEvent {

}
