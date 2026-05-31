package com.example.clinic.projection;

public interface AppointmentBySpecialtyCount {

    Long getSpecialtyId();

    String getSpecialtyName();

    Long getTotalAppointments();
}
