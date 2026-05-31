package com.example.clinic.service;

import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.request.RescheduleRequest;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.enums.AppointmentStatus;

import java.time.LocalDateTime;

public interface AppointmentService {

    // Đặt lịch khám
    AppointmentResponse book(AppointmentRequest request);

    // Lấy chi tiết lịch hẹn theo id
    AppointmentResponse getById(Long id);

    // Lấy lịch hẹn theo booking code
    AppointmentResponse getByBookingCode(String bookingCode);

    // Lấy danh sách lịch hẹn của bệnh nhân
    PageResponse<AppointmentResponse> getByPatient(Long patientId, int page, int size);

    // Lấy danh sách lịch hẹn của bác sĩ
    PageResponse<AppointmentResponse> getByDoctor(Long doctorId, int page, int size);

    // Tìm kiếm lịch hẹn — dùng cho Admin dashboard
    PageResponse<AppointmentResponse> search(
            Long patientId, Long doctorId, AppointmentStatus status,
            LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size);

    // Xác nhận lịch hẹn (bác sĩ xác nhận)
    AppointmentResponse confirm(Long id);

    // Hủy lịch hẹn
    AppointmentResponse cancel(Long id, CancelRequest request);

    // Đổi lịch hẹn
    AppointmentResponse reschedule(Long id, RescheduleRequest request);
}
