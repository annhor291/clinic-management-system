package com.example.clinic.service;

import com.example.clinic.dto.request.DoctorScheduleRequest;
import com.example.clinic.dto.response.DoctorScheduleResponse;

import java.time.LocalDate;
import java.util.List;

public interface DoctorScheduleService {

    // Tạo ca làm việc → tự động sinh các TimeSlot
    DoctorScheduleResponse create(DoctorScheduleRequest request);

    // Lấy chi tiết ca làm việc kèm danh sách slot
    DoctorScheduleResponse getById(Long id);

    // Lấy lịch làm việc của bác sĩ trong khoảng ngày
    List<DoctorScheduleResponse> getByDoctorAndDateRange(
            Long doctorId, LocalDate startDate, LocalDate endDate);

    // Lấy lịch làm việc theo tuần (7 ngày từ ngày bắt đầu)
    List<DoctorScheduleResponse> getWeeklySchedule(Long doctorId, LocalDate weekStart);

    // Vô hiệu hoá ca làm việc (không xoá, chỉ set active = false)
    DoctorScheduleResponse deactivate(Long id);

    // Xoá ca làm việc (chỉ xoá được nếu chưa có slot nào được đặt)
    void delete(Long id);

    // Kích hoạt lại ca làm việc
    DoctorScheduleResponse activate(Long id);
}
