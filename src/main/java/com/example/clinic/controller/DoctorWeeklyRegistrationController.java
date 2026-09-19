package com.example.clinic.controller;

import com.example.clinic.dto.request.RegistrationRejectRequest;
import com.example.clinic.dto.request.WeeklyRegistrationRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.WeeklyRegistrationResponse;
import com.example.clinic.service.DoctorWeeklyRegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-schedule/registrations")
@RequiredArgsConstructor
public class DoctorWeeklyRegistrationController {

    private final DoctorWeeklyRegistrationService registrationService;

    // POST /api/v1/work-schedule/registrations
    // Bác sĩ đăng ký lịch làm việc cho 1 tuần
    @PostMapping
    public ResponseEntity<ApiResponse<WeeklyRegistrationResponse>> submit(
            @Valid @RequestBody WeeklyRegistrationRequest request) {
        WeeklyRegistrationResponse response = registrationService.submit(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Nộp đăng ký lịch làm việc thành công", response));
    }

    // GET /api/v1/work-schedule/registrations/me?page=0&size=10
    // Bác sĩ xem lại lịch sử đăng ký của chính mình
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<WeeklyRegistrationResponse>>> getMyRegistrations(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đăng ký thành công",
                registrationService.getMyRegistrations(page, size)));
    }

    // GET /api/v1/work-schedule/registrations/pending?page=0&size=10
    // Admin/Receptionist xem hàng đợi đang chờ duyệt
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<WeeklyRegistrationResponse>>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chờ duyệt thành công",
                registrationService.getPending(page, size)));
    }

    // GET /api/v1/work-schedule/registrations/doctor/{doctorId}?page=0&size=10
    // Admin/Receptionist xem lịch sử đăng ký của 1 bác sĩ cụ thể
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<PageResponse<WeeklyRegistrationResponse>>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đăng ký thành công",
                registrationService.getByDoctor(doctorId, page, size)));
    }

    // GET /api/v1/work-schedule/registrations/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WeeklyRegistrationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin đăng ký thành công",
                registrationService.getById(id)));
    }

    // PUT /api/v1/work-schedule/registrations/{id}/approve
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<WeeklyRegistrationResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Duyệt đăng ký thành công",
                registrationService.approve(id)));
    }

    // PUT /api/v1/work-schedule/registrations/{id}/reject
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<WeeklyRegistrationResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody RegistrationRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Từ chối đăng ký thành công",
                registrationService.reject(id, request)));
    }
}
