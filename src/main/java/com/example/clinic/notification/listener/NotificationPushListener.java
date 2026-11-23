package com.example.clinic.notification.listener;

import com.example.clinic.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// Đẩy thông báo realtime tới đúng user SAU KHI transaction lưu Notification đã commit.
// Lỗi đẩy realtime không được ảnh hưởng nghiệp vụ: user vẫn đọc được qua REST.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushListener {

    public static final String USER_QUEUE = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(event.userId()), USER_QUEUE, event.notification());
        } catch (Exception e) {
            log.warn("Đẩy realtime thất bại cho userId={}: {}", event.userId(), e.getMessage());
        }
    }
}
