package com.example.clinic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

// DTO trả về thông tin ca làm việc kèm danh sách slot
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorScheduleResponse {

    private Long id;
    private Long doctorId;
    private String doctorName;

    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer slotDurationMinutes;
    private boolean active;
    private String note;

    // Tổng số slot trong ca
    private Integer totalSlots;

    // Số slot còn trống
    private Integer availableSlots;

    // Danh sách slot chi tiết
    private List<TimeSlotResponse> timeSlots;

    private LocalDateTime createdAt;
}
