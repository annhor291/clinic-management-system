package com.example.clinic.booking.event.listener;

import com.example.clinic.booking.event.*;
import com.example.clinic.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Lắng nghe domain event từ Booking Engine (đã publish sẵn từ trước, chưa từng có Listener).
// @TransactionalEventListener(AFTER_COMMIT): chỉ chạy SAU KHI transaction chính (book/confirm/cancel...)
// đã commit thành công — tránh việc gửi email/lưu notification bị rollback chung với appointment
// nếu SMTP lỗi, và tránh gửi thông báo cho 1 hành động cuối cùng bị rollback vì lý do khác.
// @Async: chạy trên thread riêng — không làm chậm response trả về cho người dùng.
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBooked(AppointmentBookedEvent event) {
        safeRun(() -> notificationService.notifyAppointmentBooked(event.appointmentId()), event.appointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onConfirmed(AppointmentConfirmedEvent event) {
        safeRun(() -> notificationService.notifyAppointmentConfirmed(event.appointmentId()), event.appointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCancelled(AppointmentCancelledEvent event) {
        safeRun(() -> notificationService.notifyAppointmentCancelled(event.appointmentId()), event.appointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCompleted(AppointmentCompletedEvent event) {
        safeRun(() -> notificationService.notifyAppointmentCompleted(event.appointmentId()), event.appointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNoShow(AppointmentNoShowEvent event) {
        safeRun(() -> notificationService.notifyAppointmentNoShow(event.appointmentId()), event.appointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onExpired(AppointmentExpiredEvent event) {
        safeRun(() -> notificationService.notifyAppointmentExpired(event.appointmentId()), event.appointmentId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onRescheduled(AppointmentRescheduledEvent event) {
        safeRun(() -> notificationService.notifyAppointmentRescheduled(
                event.oldAppointmentId(), event.newAppointmentId()), event.newAppointmentId());
    }

    // Bọc try/catch riêng cho từng listener — 1 lỗi gửi thông báo không được làm crash thread
    // hay ảnh hưởng tới listener khác đang lắng nghe cùng event
    private void safeRun(Runnable action, Long appointmentId) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("Lỗi khi xử lý thông báo cho appointment id={}", appointmentId, e);
        }
    }
}
