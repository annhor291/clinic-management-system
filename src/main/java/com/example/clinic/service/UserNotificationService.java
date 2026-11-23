package com.example.clinic.service;

import com.example.clinic.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;

public interface UserNotificationService {

    Page<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size);

    long countUnread(Long userId);

    void markRead(Long userId, Long notificationId);

    int markAllRead(Long userId);
}
