package com.example.clinic.controller;

import com.example.clinic.dto.request.*;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.UserResponse;
import com.example.clinic.dto.response.UserStatisticsResponse;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Toàn bộ API này chỉ dành cho ADMIN — phân quyền cấu hình trong SecurityConfig
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAll(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách user thành công",
                userService.getAll(keyword, role, enabled, page, size)));
    }

    // Đặt trước /{id} — Spring ưu tiên match path literal hơn path variable nên thứ tự không ảnh hưởng,
    // nhưng để dễ đọc mình đặt /me lên trước
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe() {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin tài khoản thành công",
                userService.getMe()));
    }

    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<UserStatisticsResponse>> getStatistics() {
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê thành công",
                userService.getStatistics()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin user thành công",
                userService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserResponse>> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Tạo user thành công",
                userService.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật user thành công",
                userService.update(id, request)));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<UserResponse>> updateRole(
            @PathVariable Long id, @Valid @RequestBody UserRoleUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vai trò thành công",
                userService.updateRole(id, request)));
    }

    @PutMapping("/{id}/active")
    public ResponseEntity<ApiResponse<UserResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Kích hoạt tài khoản thành công",
                userService.activate(id)));
    }

    @PutMapping("/{id}/deactive")
    public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Vô hiệu hoá tài khoản thành công",
                userService.deactivate(id)));
    }

    @PutMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<UserResponse>> lock(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Khoá tài khoản thành công",
                userService.lock(id)));
    }

    @PutMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<UserResponse>> unlock(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Mở khoá tài khoản thành công",
                userService.unlock(id)));
    }

    @PutMapping("/{id}/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable Long id, @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.resetPassword(id, request);
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xoá user thành công"));
    }

    // ===== Thao tác hàng loạt =====

    @PutMapping("/active")
    public ResponseEntity<ApiResponse<List<UserResponse>>> bulkActivate(@Valid @RequestBody BulkUserIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Kích hoạt hàng loạt thành công",
                userService.bulkActivate(request)));
    }

    @PutMapping("/deactive")
    public ResponseEntity<ApiResponse<List<UserResponse>>> bulkDeactivate(@Valid @RequestBody BulkUserIdsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vô hiệu hoá hàng loạt thành công",
                userService.bulkDeactivate(request)));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> bulkDelete(@Valid @RequestBody BulkUserIdsRequest request) {
        userService.bulkDelete(request);
        return ResponseEntity.ok(ApiResponse.success("Xoá hàng loạt thành công"));
    }
}
