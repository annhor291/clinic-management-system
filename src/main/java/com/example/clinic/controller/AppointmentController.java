package com.example.clinic.controller;

import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.request.RescheduleRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.SuggestedSlotResponse;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.mapper.AppointmentMapper;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.AppointmentService;
import com.example.clinic.service.AppointmentSuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentSuggestionService suggestionService;
    private final AppointmentMapper appointmentMapper;

    // Chọn cách nhìn theo vai trò người gọi; admin/lễ tân thấy đầy đủ
    private Object view(AppointmentResponse r) {
        if (SecurityUtil.isPatient()) return appointmentMapper.toPatientView(r);
        if (SecurityUtil.isDoctor()) return appointmentMapper.toDoctorView(r);
        return r;
    }

    private Object view(PageResponse<AppointmentResponse> page) {
        if (SecurityUtil.isAdminOrReceptionist()) return page;
        return PageResponse.builder()
                .content(page.getContent().stream().map(this::view).toList())
                .pageNumber(page.getPageNumber())
                .pageSize(page.getPageSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // GET /api/v1/appointments/{id}
    // Lấy chi tiết lịch hẹn theo id
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Object>> getById(@PathVariable Long id) {
        AppointmentResponse appointment = appointmentService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin lịch hẹn thành công", view(appointment)));
    }

    // GET /api/v1/appointments/code/{bookingCode}
    // Lấy lịch hẹn theo booking code — bệnh nhân tra cứu lịch hẹn
    @GetMapping("/code/{bookingCode}")
    public ResponseEntity<ApiResponse<Object>> getByBookingCode(
            @PathVariable String bookingCode) {
        AppointmentResponse appointment = appointmentService.getByBookingCode(bookingCode);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin lịch hẹn thành công", view(appointment)));
    }

    // GET /api/v1/appointments/patient/{patientId}?page=0&size=10
    // Lấy danh sách lịch hẹn của bệnh nhân
    @GetMapping("/patient/{patientId}")
    public ResponseEntity<ApiResponse<Object>> getByPatient(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<AppointmentResponse> result =
                appointmentService.getByPatient(patientId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch hẹn thành công", view(result)));
    }

    // GET /api/v1/appointments/doctor/{doctorId}?page=0&size=10
    // Lấy danh sách lịch hẹn của bác sĩ
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<Object>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<AppointmentResponse> result =
                appointmentService.getByDoctor(doctorId, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách lịch hẹn thành công", view(result)));
    }

    // GET /api/v1/appointments/search?patientId=1&doctorId=1&status=PENDING&fromDate=...&toDate=...
    // Tìm kiếm lịch hẹn — dùng cho Admin dashboard
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Object>> search(
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
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm lịch hẹn thành công", view(result)));
    }

    // POST /api/v1/appointments
    // Đặt lịch khám
    @PostMapping
    public ResponseEntity<ApiResponse<Object>> book(
            @Valid @RequestBody AppointmentRequest request) {

        AppointmentResponse appointment = appointmentService.book(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đặt lịch khám thành công", view(appointment)));
    }

    // PUT /api/v1/appointments/{id}/confirm
    // Lễ tân hoặc admin xác nhận lịch hẹn
    @PutMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Object>> confirm(@PathVariable Long id) {
        AppointmentResponse appointment = appointmentService.confirm(id);
        return ResponseEntity.ok(ApiResponse.success("Xác nhận lịch hẹn thành công", view(appointment)));
    }

    // PUT /api/v1/appointments/{id}/complete
    // Đánh dấu lịch hẹn đã khám xong
    @PutMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<Object>> complete(@PathVariable Long id) {
        AppointmentResponse appointment = appointmentService.complete(id);
        return ResponseEntity.ok(ApiResponse.success("Đánh dấu hoàn thành lịch hẹn thành công", view(appointment)));
    }

    // PUT /api/v1/appointments/{id}/no-show
    // Đánh dấu bệnh nhân không đến khám
    @PutMapping("/{id}/no-show")
    public ResponseEntity<ApiResponse<Object>> markNoShow(@PathVariable Long id) {
        AppointmentResponse appointment = appointmentService.markNoShow(id);
        return ResponseEntity.ok(ApiResponse.success("Đánh dấu không đến khám thành công", view(appointment)));
    }

    // PUT /api/v1/appointments/{id}/cancel
    // Hủy lịch hẹn
    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<Object>> cancel(
            @PathVariable Long id,
            @Valid @RequestBody CancelRequest request) {

        AppointmentResponse appointment = appointmentService.cancel(id, request);
        return ResponseEntity.ok(ApiResponse.success("Hủy lịch hẹn thành công", view(appointment)));
    }

    // PUT /api/v1/appointments/{id}/reschedule
    // Đổi lịch hẹn
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<ApiResponse<Object>> reschedule(
            @PathVariable Long id,
            @Valid @RequestBody RescheduleRequest request) {

        AppointmentResponse appointment = appointmentService.reschedule(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đổi lịch hẹn thành công", view(appointment)));
    }

    // GET /api/v1/appointments/{id}/suggestions
    // Gợi ý các slot thay thế cho lịch đã hủy hoặc hết hạn
    @GetMapping("/{id}/suggestions")
    public ResponseEntity<ApiResponse<List<SuggestedSlotResponse>>> suggestAlternatives(@PathVariable Long id) {
        List<SuggestedSlotResponse> result = suggestionService.suggestAlternatives(id);
        return ResponseEntity.ok(ApiResponse.success("Gợi ý lịch khám thay thế", result));
    }

    // GET /api/v1/appointments/me?page=0&size=10&date=2026-12-01&status=CONFIRMED
    // Lịch hẹn của tôi: bệnh nhân xem lịch khám, bác sĩ xem lịch làm việc; date và status là tùy chọn
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> getMine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) AppointmentStatus status) {

        PageResponse<AppointmentResponse> result =
                appointmentService.getMyAppointments(page, size, date, status);
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch hẹn của tôi thành công", view(result)));
    }
}
