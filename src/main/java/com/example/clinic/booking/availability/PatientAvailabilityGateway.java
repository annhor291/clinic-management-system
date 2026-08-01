package com.example.clinic.booking.availability;

import java.time.LocalDate;
import java.time.LocalTime;

// Trừu tượng hoá việc kiểm tra lịch hẹn hiện có của bệnh nhân — dùng cho PatientOverlapRule và BufferTimeRule.
// Tách riêng khỏi DoctorAvailabilityGateway vì đây là 2 khái niệm độc lập:
// DoctorAvailabilityGateway lo tài nguyên phía bác sĩ (slot), Gateway này lo phía bệnh nhân.
public interface PatientAvailabilityGateway {

    // Kiểm tra lịch hẹn active (PENDING/CONFIRMED) của bệnh nhân có trùng hoặc quá gần
    // khung giờ [start, end) trong ngày "date" hay không.
    PatientScheduleCheckResult check(
            Long patientId,
            LocalDate date,
            LocalTime start,
            LocalTime end,
            Long excludeAppointmentId
    );
}
