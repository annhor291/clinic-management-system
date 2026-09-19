package com.example.clinic.booking.scheduler;

import com.example.clinic.entity.Appointment;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Định kỳ quét các lịch hẹn CONFIRMED sắp tới, gửi nhắc trước 24h và 1h.
// Dùng khoảng [now, now + N giờ] (không phải khoảng hẹp đúng N giờ) kết hợp cờ reminderXSent
// để đảm bảo idempotent và chịu được việc server tạm dừng/khởi động lại — appointment sẽ được
// nhắc ngay khi rơi vào cửa sổ N giờ tới, không bị "lọt" nếu job không chạy đúng thời điểm N giờ chính xác.
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentReminderScheduler {

    private final AppointmentRepository appointmentRepository;
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${notification.reminder-check-interval-ms:900000}")
    @Transactional
    public void sendReminders() {
        sendReminder24h();
        sendReminder1h();
    }

    private void sendReminder24h() {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> appointments = appointmentRepository.findAppointmentsForReminder24h(now, now.plusHours(24));

        for (Appointment appt : appointments) {
            try {
                notificationService.notifyAppointmentReminder24h(appt.getId());
                appt.setReminder24hSent(true);
                appointmentRepository.save(appt);
            } catch (Exception e) {
                log.error("Lỗi khi gửi nhắc 24h cho appointment id={}", appt.getId(), e);
            }
        }
    }

    private void sendReminder1h() {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> appointments = appointmentRepository.findAppointmentsForReminder1h(now, now.plusHours(1));

        for (Appointment appt : appointments) {
            try {
                notificationService.notifyAppointmentReminder1h(appt.getId());
                appt.setReminder1hSent(true);
                appointmentRepository.save(appt);
            } catch (Exception e) {
                log.error("Lỗi khi gửi nhắc 1h cho appointment id={}", appt.getId(), e);
            }
        }
    }
}
