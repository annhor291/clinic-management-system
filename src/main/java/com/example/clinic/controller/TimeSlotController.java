package com.example.clinic.controller;

import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.TimeSlotResponse;
import com.example.clinic.service.TimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/time-slots")
@RequiredArgsConstructor
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    // GET /api/v1/time-slots/{id}
    // Lấy chi tiết 1 slot
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TimeSlotResponse>> getById(@PathVariable Long id) {
        TimeSlotResponse slot = timeSlotService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin slot thành công", slot));
    }

    // GET /api/v1/time-slots/doctor/{doctorId}?date=2026-05-17
    // Lấy tất cả slot của bác sĩ trong 1 ngày
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getByDoctorAndDate(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<TimeSlotResponse> slots = timeSlotService.getByDoctorAndDate(doctorId, date);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách slot thành công", slots));
    }

    // GET /api/v1/time-slots/doctor/{doctorId}/available?date=2026-05-17
    // Lấy slot còn trống của bác sĩ trong 1 ngày — chức năng CHECK LỊCH TRỐNG
    @GetMapping("/doctor/{doctorId}/available")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getAvailableByDoctorAndDate(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        List<TimeSlotResponse> slots = timeSlotService.getAvailableByDoctorAndDate(doctorId, date);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách slot trống thành công", slots));
    }

    // GET /api/v1/time-slots/doctor/{doctorId}/available/range?startDate=2026-05-17&endDate=2026-05-24
    // Lấy slot còn trống của bác sĩ trong khoảng ngày
    @GetMapping("/doctor/{doctorId}/available/range")
    public ResponseEntity<ApiResponse<List<TimeSlotResponse>>> getAvailableByDoctorAndDateRange(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<TimeSlotResponse> slots =
                timeSlotService.getAvailableByDoctorAndDateRange(doctorId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách slot trống thành công", slots));
    }
}
