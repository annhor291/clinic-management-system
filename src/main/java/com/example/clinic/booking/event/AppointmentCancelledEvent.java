package com.example.clinic.booking.event;

public record AppointmentCancelledEvent(Long appointmentId) implements AppointmentEvent {

}
