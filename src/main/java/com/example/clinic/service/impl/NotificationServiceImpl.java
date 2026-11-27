package com.example.clinic.service.impl;

import com.example.clinic.dto.response.NotificationResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.NotificationStatus;
import com.example.clinic.entity.enums.NotificationType;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.notification.event.NotificationCreatedEvent;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.NotificationRepository;
import com.example.clinic.repository.PaymentRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.EmailService;
import com.example.clinic.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Chịu trách nhiệm: build nội dung thông báo theo từng loại sự kiện, lưu vào bảng notifications,
// gửi email đồng bộ (sendEmailSync) và cập nhật đúng trạng thái SENT/FAILED.
// Được gọi bởi AppointmentNotificationListener (đã chạy @Async từ trước) — nên gọi đồng bộ
// ở đây không làm block request HTTP gốc.
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    @Transactional
    public void notifyAppointmentBooked(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Đặt lịch khám thành công";
        String content = "Lịch hẹn của bạn với " + doctorLabel(appt) + " vào " +
                appt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                " đã được ghi nhận, mã lịch hẹn: " + appt.getBookingCode() +
                ". Vui lòng chờ xác nhận từ phòng khám.";
        sendAndPersist(appt, NotificationType.APPOINTMENT_BOOKED, title, content);
    }

    @Override
    @Transactional
    public void notifyAppointmentConfirmed(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Lịch hẹn đã được xác nhận";
        String content = "Lịch hẹn " + appt.getBookingCode() + " với " + doctorLabel(appt) +
                " vào " + appt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                " đã được phòng khám xác nhận.";
        sendAndPersist(appt, NotificationType.APPOINTMENT_CONFIRMED, title, content);
    }

    @Override
    @Transactional
    public void notifyAppointmentCancelled(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String reason = appt.getCancellationReason() != null ? appt.getCancellationReason() : "Không rõ lý do";
        String base = "Lịch hẹn " + appt.getBookingCode() + " với " + doctorLabel(appt) +
                " vào " + appt.getAppointmentTime().format(DATE_TIME_FORMATTER);

        String title;
        String content;
        String emailBody;
        if (appt.isCancelledByClinic()) {
            title = "Phòng khám đã hủy lịch hẹn của bạn";
            content = "Rất tiếc, " + base + " đã bị phòng khám hủy. Lý do: " + reason +
                    ". Bạn có muốn hỗ trợ đặt lịch tương tự không? Hãy mở lịch hẹn này để xem các khung giờ gợi ý.";
            // Trong app thì bấm thẳng vào thông báo; email cần thêm link để mở đúng lịch hẹn
            emailBody = content + "<br/><br/><a href=\"" + frontendUrl + "/appointments/" + appt.getId()
                    + "\">Xem các khung giờ gợi ý</a>";
        } else {
            title = "Lịch hẹn đã bị hủy";
            content = base + " đã bị hủy. Lý do: " + reason;
            emailBody = content;
        }
        sendAndPersist(appt, NotificationType.APPOINTMENT_CANCELLED, title, content, emailBody);
    }

    @Override
    @Transactional
    public void notifyAppointmentCompleted(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Đã hoàn thành lịch khám";
        String content = "Lịch hẹn " + appt.getBookingCode() + " với " + doctorLabel(appt) +
                " đã hoàn thành. Cảm ơn bạn đã sử dụng dịch vụ của phòng khám.";
        sendAndPersist(appt, NotificationType.APPOINTMENT_COMPLETED, title, content);
    }

    @Override
    @Transactional
    public void notifyAppointmentNoShow(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Bạn đã không đến khám theo lịch hẹn";
        String content = "Lịch hẹn " + appt.getBookingCode() + " với " + doctorLabel(appt) +
                " vào " + appt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                " đã được đánh dấu là không đến khám. Vui lòng đặt lịch mới nếu vẫn cần khám.";
        sendAndPersist(appt, NotificationType.APPOINTMENT_NO_SHOW, title, content);
    }

    @Override
    @Transactional
    public void notifyAppointmentExpired(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Lịch hẹn đã hết hạn chờ xác nhận";
        String content = "Lịch hẹn " + appt.getBookingCode() + " với " + doctorLabel(appt) +
                " đã tự động hết hạn vì không được xác nhận kịp thời. Vui lòng đặt lịch lại nếu vẫn cần khám.";
        sendAndPersist(appt, NotificationType.APPOINTMENT_EXPIRED, title, content);
    }

    @Override
    @Transactional
    public void notifyAppointmentRescheduled(Long appointmentId, LocalDateTime oldAppointmentTime) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Lịch hẹn đã được đổi";
        String content = "Lịch hẹn " + appt.getBookingCode() + " đã được đổi từ " +
                oldAppointmentTime.format(DATE_TIME_FORMATTER) + " sang " +
                appt.getAppointmentTime().format(DATE_TIME_FORMATTER) + " với " + doctorLabel(appt) + ".";
        sendAndPersist(appt, NotificationType.APPOINTMENT_RESCHEDULED, title, content);
    }

    @Override
    @Transactional
    public void notifyAppointmentReminder24h(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Nhắc lịch khám (còn 24 giờ)";
        String content = "Bạn có lịch hẹn với " + doctorLabel(appt) + " vào " +
                appt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                " (còn khoảng 24 giờ nữa). Vui lòng đến đúng giờ.";
        sendAndPersist(appt, NotificationType.APPOINTMENT_REMINDER_24H, title, content);
    }

    @Override
    @Transactional
    public void notifyAppointmentReminder1h(Long appointmentId) {
        Appointment appt = findAppointmentOrThrow(appointmentId);
        String title = "Nhắc lịch khám (còn 1 giờ)";
        String content = "Bạn có lịch hẹn với " + doctorLabel(appt) + " vào " +
                appt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                " (còn khoảng 1 giờ nữa). Vui lòng đến đúng giờ.";
        sendAndPersist(appt, NotificationType.APPOINTMENT_REMINDER_1H, title, content);
    }

    @Override
    @Transactional
    public void notifyPaymentSucceeded(Long paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);
        Appointment appt = findAppointmentOrThrow(payment.getAppointment().getId());
        String title = "Thanh toán thành công";
        String content = "Bạn đã thanh toán " + formatMoney(payment.getAmount()) + " cho lịch hẹn " +
                appt.getBookingCode() + " với " + doctorLabel(appt) + " vào " +
                appt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                ". Mã giao dịch: " + payment.getTransactionCode() + ".";
        sendAndPersist(appt, NotificationType.PAYMENT_SUCCESS, title, content);
    }

    @Override
    @Transactional
    public void notifyPaymentRefunded(Long paymentId) {
        Payment payment = findPaymentOrThrow(paymentId);
        Appointment appt = findAppointmentOrThrow(payment.getAppointment().getId());
        String title = "Đã hoàn tiền";
        String content = "Phòng khám đã gửi yêu cầu hoàn " + formatMoney(payment.getRefundAmount()) +
                " cho lịch hẹn " + appt.getBookingCode() + " (mã giao dịch " + payment.getTransactionCode() +
                "). Lý do: " + payment.getRefundReason() +
                ". Thời gian tiền về tài khoản phụ thuộc vào ngân hàng của bạn.";
        sendAndPersist(appt, NotificationType.PAYMENT_REFUNDED, title, content);
    }

    @Override
    @Transactional
    public void notifyWelcome(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với id: " + userId));
        String title = "Chào mừng bạn đến với phòng khám";
        String content = "Xin chào " + user.getDisplayName() + ", tài khoản của bạn đã sẵn sàng. "
                + "Bạn có thể đặt lịch khám trực tuyến, thanh toán và theo dõi lịch hẹn ngay trên hệ thống.";

        Notification notification = notificationRepository.save(Notification.builder()
                .user(user)
                .type(NotificationType.WELCOME)
                .title(title)
                .content(content)
                .status(NotificationStatus.PENDING)
                .scheduledAt(LocalDateTime.now())
                .build());

        boolean success = emailService.sendEmailSync(user.getEmail(), title, content);
        if (success) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage("Gửi email thất bại — xem log server để biết chi tiết");
            notification.setRetryCount(notification.getRetryCount() + 1);
        }
        notificationRepository.save(notification);

        eventPublisher.publishEvent(
                new NotificationCreatedEvent(user.getId(), NotificationResponse.from(notification)));
    }

    @Override
    @Transactional
    public int broadcastGeneral(String title, String content, Role targetRole) {
        List<User> recipients = userRepository.findBroadcastRecipients(targetRole);
        LocalDateTime now = LocalDateTime.now();

        // Thông báo trong app, KHÔNG gửi email hàng loạt (dễ bị SMTP giới hạn hoặc coi là spam)
        List<Notification> saved = notificationRepository.saveAll(recipients.stream()
                .map(u -> Notification.builder()
                        .user(u)
                        .type(NotificationType.GENERAL)
                        .title(title)
                        .content(content)
                        .status(NotificationStatus.SENT)
                        .scheduledAt(now)
                        .sentAt(now)
                        .build())
                .toList());

        saved.forEach(n -> eventPublisher.publishEvent(
                new NotificationCreatedEvent(n.getUser().getId(), NotificationResponse.from(n))));
        return saved.size();
    }

    // ===== Private helpers =====

    private Appointment findAppointmentOrThrow(Long id) {
        return appointmentRepository.findByIdWithPatientAndDoctorUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với id: " + id));
    }

    private String doctorLabel(Appointment appt) {
        return "bác sĩ " + appt.getDoctor().getFullName();
    }

    private void sendAndPersist(Appointment appt, NotificationType type, String title, String content) {
        sendAndPersist(appt, type, title, content, content);
    }

    // Lưu Notification (PENDING) → gửi email đồng bộ → cập nhật SENT/FAILED
    // emailBody có thể khác content (vd thêm link), còn content là bản hiển thị trong app
    private void sendAndPersist(Appointment appt, NotificationType type, String title,
                                String content, String emailBody) {
        Patient patient = appt.getPatient();

        // Người nhận Notification trong hệ thống: chính chủ nếu có, ngược lại là người quản lý hồ sơ
        User recipient = patient.getUser() != null ? patient.getUser() : patient.getManagedBy();

        if (recipient == null) {
            log.warn("Không tìm được người nhận thông báo cho appointment id={} — bỏ qua", appt.getId());
            return;
        }

        Notification notification = Notification.builder()
                .user(recipient)
                .appointment(appt)
                .type(type)
                .title(title)
                .content(content)
                .status(NotificationStatus.PENDING)
                .scheduledAt(LocalDateTime.now())
                .build();

        notification = notificationRepository.save(notification);

        boolean success = emailService.sendEmailSync(recipient.getEmail(), title, emailBody);

        if (success) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage("Gửi email thất bại — xem log server để biết chi tiết");
            notification.setRetryCount(notification.getRetryCount() + 1);
        }

        notificationRepository.save(notification);

        // Đẩy realtime sau khi commit (NotificationPushListener); gửi email thất bại vẫn có thông báo trong app
        eventPublisher.publishEvent(
                new NotificationCreatedEvent(recipient.getId(), NotificationResponse.from(notification)));

        // Hồ sơ người thân có contactEmail riêng, khác với email người quản lý → gửi thêm 1 email thô
        // (không tạo Notification record vì contactEmail không gắn với User thật nào trong hệ thống)
        if (patient.getUser() == null && patient.getContactEmail() != null
                && !patient.getContactEmail().equalsIgnoreCase(recipient.getEmail())) {
            emailService.sendEmail(patient.getContactEmail(), title, emailBody);
        }
    }

    private Payment findPaymentOrThrow(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy giao dịch với id: " + id));
    }

    private String formatMoney(BigDecimal amount) {
        return NumberFormat.getInstance(Locale.of("vi", "VN")).format(amount) + " VND";
    }

}
