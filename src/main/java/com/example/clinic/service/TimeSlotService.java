package com.example.clinic.service;

import com.example.clinic.dto.response.TimeSlotResponse;

import java.time.LocalDate;
import java.util.List;

public interface TimeSlotService {

    // Lấy tất cả slot của bác sĩ trong 1 ngày
    List<TimeSlotResponse> getByDoctorAndDate(Long doctorId, LocalDate date);

    // Lấy slot còn trống của bác sĩ trong 1 ngày — chức năng check lịch trống
    List<TimeSlotResponse> getAvailableByDoctorAndDate(Long doctorId, LocalDate date);

    // Lấy slot còn trống của bác sĩ trong khoảng ngày
    List<TimeSlotResponse> getAvailableByDoctorAndDateRange(
            Long doctorId, LocalDate startDate, LocalDate endDate);

    // Lấy chi tiết 1 slot
    TimeSlotResponse getById(Long id);
}
