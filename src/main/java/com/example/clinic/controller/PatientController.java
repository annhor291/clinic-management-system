package com.example.clinic.controller;

import com.example.clinic.dto.request.PatientCreateRequest;
import com.example.clinic.dto.request.PatientUpdateRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.PatientResponse;
import com.example.clinic.service.PatientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    // GET /api/v1/patients?keyword=nguyen&page=0&size=10
    // Lấy danh sách bệnh nhân có pagination + filter theo tên hoặc SĐT
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PatientResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<PatientResponse> result = patientService.getAll(keyword, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách bệnh nhân thành công", result));
    }

    // GET /api/v1/patients/{id}
    // Lấy chi tiết 1 bệnh nhân theo id
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientResponse>> getById(@PathVariable Long id) {
        PatientResponse patient = patientService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin bệnh nhân thành công", patient));
    }

    // GET /api/v1/patients/user/{userId}
    // Lấy hồ sơ bệnh nhân theo userId
    // Dùng khi bệnh nhân đăng nhập và xem hồ sơ của chính mình
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<PatientResponse>> getByUserId(@PathVariable Long userId) {
        PatientResponse patient = patientService.getByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin bệnh nhân thành công", patient));
    }

    // POST /api/v1/patients?userId=1
    // Tạo mới hồ sơ bệnh nhân gắn với userId
    @PostMapping
    public ResponseEntity<ApiResponse<PatientResponse>> create(
            @RequestParam Long userId,
            @Valid @RequestBody PatientCreateRequest request) {

        PatientResponse patient = patientService.create(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo hồ sơ bệnh nhân thành công", patient));
    }

    // PUT /api/v1/patients/{id}
    // Cập nhật thông tin bệnh nhân
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PatientResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody PatientUpdateRequest request) {

        PatientResponse patient = patientService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật thông tin bệnh nhân thành công", patient));
    }

    // DELETE /api/v1/patients/{id}
    // Xoá bệnh nhân
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        patientService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xoá bệnh nhân thành công"));
    }
}
