package com.example.clinic.service;

import com.example.clinic.dto.request.DoctorScheduleRequest;
import com.example.clinic.dto.response.DoctorScheduleResponse;
import com.example.clinic.entity.enums.ShiftType;

import java.time.LocalDate;
import java.time.LocalTime;
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

    // Sinh 1 ca làm việc tự động từ luồng đăng ký lịch tuần đã được duyệt.
    // Không áp dụng các validate dành cho luồng tạo thủ công qua API công khai.
    DoctorScheduleResponse createFromApprovedRegistration(Long doctorId, LocalDate workDate,
                                                          LocalTime startTime, LocalTime endTime,
                                                          ShiftType shiftType);
}
