package com.example.clinic.controller;

import com.example.clinic.dto.request.DoctorScheduleRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.DoctorScheduleResponse;
import com.example.clinic.service.DoctorScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/doctor-schedules")
@RequiredArgsConstructor
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    // GET /api/v1/doctor-schedules/{id}
    // Lấy chi tiết ca làm việc kèm danh sách slot
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorScheduleResponse>> getById(@PathVariable Long id) {
        DoctorScheduleResponse schedule = scheduleService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin ca làm việc thành công", schedule));
    }

    // GET /api/v1/doctor-schedules/doctor/{doctorId}?startDate=2026-05-01&endDate=2026-05-31
    // Lấy lịch làm việc của bác sĩ trong khoảng ngày
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<List<DoctorScheduleResponse>>> getByDoctorAndDateRange(
            @PathVariable Long doctorId,
            // @DateTimeFormat: convert String "2026-05-01" → LocalDate
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        List<DoctorScheduleResponse> schedules =
                scheduleService.getByDoctorAndDateRange(doctorId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch làm việc thành công", schedules));
    }

    // GET /api/v1/doctor-schedules/doctor/{doctorId}/weekly?weekStart=2026-05-13
    // Lấy lịch làm việc theo tuần
    @GetMapping("/doctor/{doctorId}/weekly")
    public ResponseEntity<ApiResponse<List<DoctorScheduleResponse>>> getWeeklySchedule(
            @PathVariable Long doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {

        List<DoctorScheduleResponse> schedules =
                scheduleService.getWeeklySchedule(doctorId, weekStart);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch tuần thành công", schedules));
    }

    // POST /api/v1/doctor-schedules
    // Tạo ca làm việc → tự động sinh TimeSlot
    @PostMapping
    public ResponseEntity<ApiResponse<DoctorScheduleResponse>> create(
            @Valid @RequestBody DoctorScheduleRequest request) {

        DoctorScheduleResponse schedule = scheduleService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo ca làm việc thành công", schedule));
    }

    // PUT /api/v1/doctor-schedules/{id}/deactivate
    // Vô hiệu hoá ca làm việc
    @PutMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<DoctorScheduleResponse>> deactivate(@PathVariable Long id) {
        DoctorScheduleResponse schedule = scheduleService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.success("Vô hiệu hoá ca làm việc thành công", schedule));
    }

    // PUT /api/v1/doctor-schedules/{id}/activate
    // Kích hoạt laại ca làm việc
    @PutMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<DoctorScheduleResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Kích hoạt ca làm việc thành công",
                scheduleService.activate(id)));
    }

    // DELETE /api/v1/doctor-schedules/{id}
    // Xoá ca làm việc
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xoá ca làm việc thành công"));
    }
}
