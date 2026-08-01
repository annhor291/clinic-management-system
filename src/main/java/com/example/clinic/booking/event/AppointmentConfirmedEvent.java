package com.example.clinic.booking.event;

public record AppointmentConfirmedEvent(Long appointmentId) implements AppointmentEvent {


}
