package com.example.clinic.service;

import com.example.clinic.dto.request.PatientCreateRequest;
import com.example.clinic.dto.request.PatientUpdateRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.PatientResponse;

public interface PatientService {
    // Lấy danh sách bệnh nhân có pagination + filter theo keyword (tên hoặc SĐT)
    PageResponse<PatientResponse> getAll(String keyword, int page, int size);

    // Lấy chi tiết 1 bệnh nhân theo id
    PatientResponse getById(Long id);

    // Lấy thông tin bệnh nhân theo user_id (dùng khi bệnh nhân xem hồ sơ của chính mình)
    PatientResponse getByUserId(Long userId);

    // Tạo mới bệnh nhân
    PatientResponse create(Long userId, PatientCreateRequest request);

    // Cập nhật thông tin bệnh nhân
    PatientResponse update(Long id, PatientUpdateRequest request);

    // Xoá bệnh nhân
    void delete(Long id);

    PatientResponse getMe();
}
