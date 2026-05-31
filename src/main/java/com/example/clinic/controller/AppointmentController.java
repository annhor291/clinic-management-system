package com.example.clinic.controller;

import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.request.RescheduleRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.service.AppointmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    // GET /api/v1/appointments/{id}
    // Lấy chi tiết lịch hẹn theo id
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getById(@PathVariable Long id) {
        AppointmentResponse appointment = appointmentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin lịch hẹn thành công", appointment));
    }

    // GET /api/v1/appointments/code/{bookingCode}
    // Lấy lịch hẹn theo booking code — bệnh nhân tra cứu lịch hẹn
    @GetMapping("/code/{bookingCode}")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getByBookingCode(
            @PathVariable String bookingCode) {
        AppointmentResponse appointment = appointmentService.getByBookingCode(bookingCode);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin lịch hẹn thành công", appointment));
    }

    // GET /api/v1/appointments/patient/{patientId}?page=0&size=10
    // Lấy danh sách lịch hẹn của bệnh nhân
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<AppointmentResponse> result =
                appointmentService.getByPatient(patientId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch hẹn thành công", result));
    }

    // GET /api/v1/appointments/doctor/{doctorId}?page=0&size=10
    // Lấy danh sách lịch hẹn của bác sĩ
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<AppointmentResponse> result =
                appointmentService.getByDoctor(doctorId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch hẹn thành công", result));
    }

    // GET /api/v1/appointments/search?patientId=1&doctorId=1&status=PENDING&fromDate=...&toDate=...
    // Tìm kiếm lịch hẹn — dùng cho Admin dashboard
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<AppointmentResponse>>> search(
            @RequestParam(required = false) Long patientId,
            @RequestParam(required = false) Long doctorId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<AppointmentResponse> result =
                appointmentService.search(patientId, doctorId, status, fromDate, toDate, page, size);
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm lịch hẹn thành công", result));
    }

    // POST /api/v1/appointments
    // Đặt lịch khám
    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentResponse>> book(
            @Valid @RequestBody AppointmentRequest request) {

        AppointmentResponse appointment = appointmentService.book(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt lịch khám thành công", appointment));
    }

    // PUT /api/v1/appointments/{id}/confirm
    // Bác sĩ xác nhận lịch hẹn
    @PutMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<AppointmentResponse>> confirm(@PathVariable Long id) {
        AppointmentResponse appointment = appointmentService.confirm(id);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận lịch hẹn thành công", appointment));
    }

    // PUT /api/v1/appointments/{id}/cancel
    // Hủy lịch hẹn
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<AppointmentResponse>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request) {

        AppointmentResponse appointment = appointmentService.cancel(id, request);
        return ResponseEntity.ok(ApiResponse.success("Hủy lịch hẹn thành công", appointment));
    }

    // PUT /api/v1/appointments/{id}/reschedule
    // Đổi lịch hẹn
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<AppointmentResponse>> reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request) {

        AppointmentResponse appointment = appointmentService.reschedule(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đổi lịch hẹn thành công", appointment));
    }
}
