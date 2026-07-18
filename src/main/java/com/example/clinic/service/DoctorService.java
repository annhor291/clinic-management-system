package com.example.clinic.service;

import com.example.clinic.dto.request.DoctorCreateRequest;
import com.example.clinic.dto.request.DoctorUpdateRequest;
import com.example.clinic.dto.response.DoctorResponse;
import com.example.clinic.dto.response.PageResponse;

public interface DoctorService {
    // Lấy danh sách bác sĩ có pagination + filter theo tên, chuyên khoa, trạng thái
    PageResponse<DoctorResponse> getAll(String keyword, Long specialtyId, Boolean active, int page, int size);

    // Lấy chi tiết 1 bác sĩ theo id
    DoctorResponse getById(Long id);

    // Lấy thông tin bác sĩ theo user_id
    DoctorResponse getByUserId(Long userId);

    // Tạo mới bác sĩ
    DoctorResponse create(Long userId, DoctorCreateRequest request);

    // Cập nhật thông tin bác sĩ
    DoctorResponse update(Long id, DoctorUpdateRequest request);

    // Xoá bác sĩ
    void delete(Long id);

    // Kích hoạt bác sĩ
    DoctorResponse activate(Long id);

    // Vô hiệu hoá bác sĩ
    DoctorResponse deactivate(Long id);

    // Bác sĩ xem hồ sơ của chính mình
    DoctorResponse getMe();
}
