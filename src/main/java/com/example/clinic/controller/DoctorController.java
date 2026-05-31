package com.example.clinic.controller;

import com.example.clinic.dto.request.DoctorCreateRequest;
import com.example.clinic.dto.request.DoctorUpdateRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.DoctorResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.service.DoctorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorService doctorService;

    // GET /api/v1/doctors?keyword=nguyen&specialtyId=1&active=true&page=0&size=10
    // Lấy danh sách bác sĩ có pagination + filter theo tên, chuyên khoa, trạng thái
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DoctorResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long specialtyId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<DoctorResponse> result = doctorService.getAll(keyword, specialtyId, active, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bác sĩ thành công", result));
    }

    // GET /api/v1/doctors/{id}
    // Lấy chi tiết 1 bác sĩ
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> getById(@PathVariable Long id) {
        DoctorResponse doctor = doctorService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin bác sĩ thành công", doctor));
    }

    // GET /api/v1/doctors/user/{userId}
    // Lấy hồ sơ bác sĩ theo userId
    // Dùng khi bác sĩ đăng nhập và xem hồ sơ của chính mình
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<DoctorResponse>> getByUserId(@PathVariable Long userId) {
        DoctorResponse doctor = doctorService.getByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin bác sĩ thành công", doctor));
    }

    // POST /api/v1/doctors?userId=1
    // Tạo mới hồ sơ bác sĩ gắn với userId
    @PostMapping
    public ResponseEntity<ApiResponse<DoctorResponse>> create(
            @RequestParam Long userId,
            @Valid @RequestBody DoctorCreateRequest request) {

        DoctorResponse doctor = doctorService.create(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo hồ sơ bác sĩ thành công", doctor));
    }

    // PUT /api/v1/doctors/{id}
    // Cập nhật thông tin bác sĩ
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DoctorResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequest request) {

        DoctorResponse doctor = doctorService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin bác sĩ thành công", doctor));
    }

    // PUT /api/v1/doctors/{id}/toggle-active
    // Kích hoạt / vô hiệu hoá bác sĩ
    @PutMapping("/{id}/toggle-active")
    public ResponseEntity<ApiResponse<DoctorResponse>> toggleActive(@PathVariable Long id) {
        DoctorResponse doctor = doctorService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái bác sĩ thành công", doctor));
    }

    // DELETE /api/v1/doctors/{id}
    // Xoá bác sĩ
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        doctorService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xoá bác sĩ thành công"));
    }
}
