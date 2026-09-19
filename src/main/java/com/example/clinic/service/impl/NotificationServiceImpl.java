package com.example.clinic.service.impl;

import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.Notification;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.NotificationStatus;
import com.example.clinic.entity.enums.NotificationType;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.NotificationRepository;
import com.example.clinic.service.EmailService;
import com.example.clinic.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
        String title = "Lịch hẹn đã bị hủy";
        String reason = appt.getCancellationReason() != null ? appt.getCancellationReason() : "Không rõ lý do";
        String content = "Lịch hẹn " + appt.getBookingCode() + " với " + doctorLabel(appt) +
                " vào " + appt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                " đã bị hủy. Lý do: " + reason;
        sendAndPersist(appt, NotificationType.APPOINTMENT_CANCELLED, title, content);
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
    public void notifyAppointmentRescheduled(Long oldAppointmentId, Long newAppointmentId) {
        Appointment newAppt = findAppointmentOrThrow(newAppointmentId);
        String title = "Lịch hẹn đã được đổi";
        String content = "Lịch hẹn của bạn đã được đổi sang " + doctorLabel(newAppt) +
                " vào " + newAppt.getAppointmentTime().format(DATE_TIME_FORMATTER) +
                ", mã lịch hẹn mới: " + newAppt.getBookingCode() + ".";
        sendAndPersist(newAppt, NotificationType.APPOINTMENT_RESCHEDULED, title, content);
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

    // ===== Private helpers =====

    private Appointment findAppointmentOrThrow(Long id) {
        return appointmentRepository.findByIdWithPatientAndDoctorUser(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với id: " + id));
    }

    private String doctorLabel(Appointment appt) {
        return "bác sĩ " + appt.getDoctor().getFullName();
    }

    // Lưu Notification (PENDING) → gửi email đồng bộ → cập nhật SENT/FAILED
    private void sendAndPersist(Appointment appt, NotificationType type, String title, String content) {
        User recipient = appt.getPatient().getUser();

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

        boolean success = emailService.sendEmailSync(recipient.getEmail(), title, content);

        if (success) {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
        } else {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setErrorMessage("Gửi email thất bại — xem log server để biết chi tiết");
            notification.setRetryCount(notification.getRetryCount() + 1);
        }

        notificationRepository.save(notification);
    }

}
