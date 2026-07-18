package com.example.clinic.service;

import com.example.clinic.dto.request.*;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.UserResponse;
import com.example.clinic.dto.response.UserStatisticsResponse;
import com.example.clinic.entity.enums.Role;

import java.util.List;

public interface UserService {

    PageResponse<UserResponse> getAll(String keyword, Role role, Boolean enabled, int page, int size);

    UserResponse getById(Long id);

    // Lấy hồ sơ của admin đang đăng nhập
    UserResponse getMe();

    UserResponse create(UserCreateRequest request);

    UserResponse update(Long id, UserUpdateRequest request);

    // Đổi vai trò của user
    UserResponse updateRole(Long id, UserRoleUpdateRequest request);

    // Kích hoạt / vô hiệu hoá (do admin chủ động bật/tắt)
    UserResponse activate(Long id);
    UserResponse deactivate(Long id);

    // Khoá / mở khoá vì lý do bảo mật
    UserResponse lock(Long id);
    UserResponse unlock(Long id);

    void resetPassword(Long id, AdminResetPasswordRequest request);

    // Xoá mềm
    void delete(Long id);

    UserStatisticsResponse getStatistics();

    // ===== Thao tác hàng loạt =====
    List<UserResponse> bulkActivate(BulkUserIdsRequest request);
    List<UserResponse> bulkDeactivate(BulkUserIdsRequest request);
    void bulkDelete(BulkUserIdsRequest request);

}
