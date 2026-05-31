package com.example.clinic.service;

import com.example.clinic.dto.request.SpecialtyCreateRequest;
import com.example.clinic.dto.request.SpecialtyUpdateRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.SpecialtyResponse;

import java.util.List;

public interface SpecialtyService {

    // Lấy tất cả chuyên khoa đang active (dùng cho dropdown, không cần pagination)
    List<SpecialtyResponse> getAllActive();

    // Lấy danh sách chuyên khoa có pagination + filter
    PageResponse<SpecialtyResponse> getAll(String keyword, Boolean active, int page, int size);

    // Lấy chi tiết 1 chuyên khoa theo id
    SpecialtyResponse getById(Long id);

    // Tạo mới chuyên khoa
    SpecialtyResponse create(SpecialtyCreateRequest request);

    // Cập nhật chuyên khoa
    SpecialtyResponse update(Long id, SpecialtyUpdateRequest request);

    // Xoá chuyên khoa (chỉ xoá được nếu không có bác sĩ nào thuộc chuyên khoa này)
    void delete(Long id);
}
