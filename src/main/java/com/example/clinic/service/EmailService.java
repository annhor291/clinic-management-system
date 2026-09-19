package com.example.clinic.service;

public interface EmailService {

    // Gửi email đơn giản
    void sendEmail(String to, String subject, String body);

    // Gửi email quên mật khẩu
    void sendForgotPasswordEmail(String to, String resetToken);

    // Gửi email xác nhận đặt lịch
    void sendAppointmentConfirmationEmail(String to, String patientName, String doctorName, String dateTime);

    // Gửi email ĐỒNG BỘ (không @Async) — chỉ dùng riêng bởi NotificationService để track
    // chính xác kết quả gửi (thành công/thất bại), lưu vào Notification.status.
    // Không dùng cho các luồng khác — 3 method trên vẫn giữ nguyên hành vi @Async cũ.
    boolean sendEmailSync(String to, String subject, String body);
}
