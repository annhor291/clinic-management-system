package com.example.clinic.controller;

import com.example.clinic.dto.request.SpecialtyCreateRequest;
import com.example.clinic.dto.request.SpecialtyUpdateRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.SpecialtyResponse;
import com.example.clinic.service.SpecialtyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/specialties")
@RequiredArgsConstructor
public class SpecialtyController {

    private final SpecialtyService specialtyService;

    // GET /api/v1/specialties/all
    // Lấy tất cả chuyên khoa đang active — dùng cho dropdown
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<SpecialtyResponse>>> getAllActive() {
        List<SpecialtyResponse> specialties = specialtyService.getAllActive();
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chuyên khoa thành công", specialties));
    }

    // GET /api/v1/specialties?keyword=nội&active=true&page=0&size=10
    // Lấy danh sách chuyên khoa có pagination + filter
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SpecialtyResponse>>> getAll(
            // @RequestParam: lấy giá trị từ query string, có giá trị mặc định nếu không truyền
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        PageResponse<SpecialtyResponse> result = specialtyService.getAll(keyword, active, page, size);
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chuyên khoa thành công", result));
    }

    // GET /api/v1/specialties/{id}
    // Lấy chi tiết 1 chuyên khoa
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> getById(@PathVariable Long id) {
        SpecialtyResponse specialty = specialtyService.getById(id);
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin chuyên khoa thành công", specialty));
    }

    // POST /api/v1/specialties
    // Tạo mới chuyên khoa
    // @Valid: kích hoạt validation từ các annotation trong SpecialtyRequest
    @PostMapping
    public ResponseEntity<ApiResponse<SpecialtyResponse>> create(
            @Valid @RequestBody SpecialtyCreateRequest request) {

        SpecialtyResponse specialty = specialtyService.create(request);
        // HTTP 201 Created thay vì 200 OK khi tạo mới thành công
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo chuyên khoa thành công", specialty));
    }

    // PUT /api/v1/specialties/{id}
    // Cập nhật chuyên khoa
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<SpecialtyResponse>> update(
            @PathVariable Long id,
            @Valid @RequestBody SpecialtyUpdateRequest request) {

        SpecialtyResponse specialty = specialtyService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật chuyên khoa thành công", specialty));
    }

    // DELETE /api/v1/specialties/{id}
    // Xoá chuyên khoa
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        specialtyService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xoá chuyên khoa thành công"));
    }
}
