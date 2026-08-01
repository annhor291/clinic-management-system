package com.example.clinic.service;

import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.request.RescheduleRequest;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.enums.AppointmentStatus;

import java.time.LocalDateTime;
import java.util.List;

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

    // Tìm id các lịch hẹn PENDING đã quá hạn xác nhận — chỉ dùng nội bộ bởi AppointmentExpiryScheduler
    List<Long> findExpirablePendingIds();

    // Chuyển 1 lịch hẹn PENDING quá hạn sang EXPIRED, giải phóng slot — chỉ dùng nội bộ bởi Scheduler
    void expireOne(Long appointmentId);

    // Tìm kiếm lịch hẹn — dùng cho Admin dashboard
    PageResponse<AppointmentResponse> search(
            Long patientId, Long doctorId, AppointmentStatus status,
            LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size);

    // Xác nhận lịch hẹn (bác sĩ xác nhận)
    AppointmentResponse confirm(Long id);

    // Đánh dấu lịch hẹn đã khám xong
    AppointmentResponse complete(Long id);

    // Đánh dấu bệnh nhân không đến khám
    AppointmentResponse markNoShow(Long id);

    // Hủy lịch hẹn
    AppointmentResponse cancel(Long id, CancelRequest request);

    // Đổi lịch hẹn
    AppointmentResponse reschedule(Long id, RescheduleRequest request);
}
