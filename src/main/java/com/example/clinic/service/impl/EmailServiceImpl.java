package com.example.clinic.service.impl;

import com.example.clinic.service.EmailService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;


    // @Async: gửi email trong thread riêng, không block request
    @Async
    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true = HTML

            mailSender.send(message);
            log.info("Đã gửi email tới {}", to);

        } catch (Exception e) {
            log.error("Gửi email thất bại tới {}: {}", to, e.getMessage());
        }
    }

    @Async
    @Override
    public void sendForgotPasswordEmail(String to, String resetToken) {
        String subject = "Đặt lại mật khẩu - Clinic Management System";
        String body = """
                <h2>Yêu cầu đặt lại mật khẩu</h2>
                <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn.</p>
                <p>Mã đặt lại mật khẩu của bạn:</p>
                <h3 style="color: #4285F4; letter-spacing: 4px;">%s</h3>
                <p>Mã này có hiệu lực trong <strong>15 phút</strong>.</p>
                <p>Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.</p>
                <br/>
                <p>Trân trọng,<br/>Clinic Management System</p>
                """.formatted(resetToken);

        sendEmail(to, subject, body);

    }

    @Async
    @Override
    public void sendAppointmentConfirmationEmail(String to, String patientName,
                                                 String doctorName, String dateTime) {

        String subject = "Xác nhận lịch hẹn - Clinic Management System";
        String body = """
                <h2>Xác nhận lịch hẹn</h2>
                <p>Xin chào <strong>%s</strong>,</p>
                <p>Lịch hẹn của bạn đã được đặt thành công với thông tin sau:</p>
                <ul>
                    <li><strong>Bác sĩ:</strong> %s</li>
                    <li><strong>Thời gian:</strong> %s</li>
                </ul>
                <p>Vui lòng đến đúng giờ.</p>
                <br/>
                <p>Trân trọng,<br/>Clinic Management System</p>
                """.formatted(patientName, doctorName, dateTime);

        sendEmail(to, subject, body);
    }


}
