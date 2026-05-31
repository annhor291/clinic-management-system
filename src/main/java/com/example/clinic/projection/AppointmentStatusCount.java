package com.example.clinic.projection;

import com.example.clinic.entity.enums.AppointmentStatus;

public interface AppointmentStatusCount {

    AppointmentStatus getStatus();

    Long getCount();
}
