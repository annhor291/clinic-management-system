package com.example.clinic.service;

import com.example.clinic.dto.request.RegistrationRejectRequest;
import com.example.clinic.dto.request.WeeklyRegistrationRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.WeeklyRegistrationResponse;
import com.example.clinic.entity.enums.RegistrationStatus;

public interface DoctorWeeklyRegistrationService {

    // Bác sĩ nộp đăng ký lịch làm việc cho 1 tuần — trả kèm cảnh báo mềm nếu trùng lịch nghỉ đã duyệt
    WeeklyRegistrationResponse submit(WeeklyRegistrationRequest request);

    WeeklyRegistrationResponse getById(Long id);

    // Bác sĩ xem lại lịch sử đăng ký của chính mình
    PageResponse<WeeklyRegistrationResponse> getMyRegistrations(int page, int size);

    // Admin/Receptionist xem lịch sử đăng ký của 1 bác sĩ cụ thể
    PageResponse<WeeklyRegistrationResponse> getByDoctor(Long doctorId, int page, int size);

    // Admin/Receptionist xem hàng đợi các đăng ký đang chờ duyệt
    PageResponse<WeeklyRegistrationResponse> getPending(int page, int size);

    WeeklyRegistrationResponse approve(Long id);

    WeeklyRegistrationResponse reject(Long id, RegistrationRejectRequest request);

    // Admin/Receptionist xem tất cả đăng ký, lọc theo trạng thái nếu có
    PageResponse<WeeklyRegistrationResponse> getAll(RegistrationStatus status, int page, int size);
}
