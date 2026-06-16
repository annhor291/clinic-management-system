package com.example.clinic.service;

public interface EmailService {

    // Gửi email đơn giản
    void sendEmail(String to, String subject, String body);

    // Gửi email quên mật khẩu
    void sendForgotPasswordEmail(String to, String resetToken);

    // Gửi email xác nhận đặt lịch
    void sendAppointmentConfirmationEmail(String to, String patientName, String doctorName, String dateTime);
}
