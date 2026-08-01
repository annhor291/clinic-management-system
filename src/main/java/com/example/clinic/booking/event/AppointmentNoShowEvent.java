package com.example.clinic.booking.event;

public record AppointmentNoShowEvent(Long appointmentId) implements AppointmentEvent {

}
