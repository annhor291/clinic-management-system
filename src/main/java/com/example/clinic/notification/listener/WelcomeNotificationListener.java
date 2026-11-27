package com.example.clinic.notification.listener;

import com.example.clinic.notification.event.UserWelcomeEvent;
import com.example.clinic.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Chạy SAU khi transaction tạo/xác minh tài khoản đã commit, trên thread riêng:
// lỗi gửi thông báo không làm hỏng việc đăng ký/đăng nhập.
@Component
@RequiredArgsConstructor
@Slf4j
public class WelcomeNotificationListener {

    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserWelcome(UserWelcomeEvent event) {
        try {
            notificationService.notifyWelcome(event.userId());
        } catch (Exception e) {
            log.warn("Gửi thông báo chào mừng thất bại cho userId={}: {}", event.userId(), e.getMessage());
        }
    }
}
