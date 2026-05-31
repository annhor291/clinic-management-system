package com.example.clinic.projection;

public interface AppointmentByDoctorCount {

    Long getDoctorId();

    String getDoctorName();

    String getSpecialtyName();

    Long getTotalAppointments();
}
