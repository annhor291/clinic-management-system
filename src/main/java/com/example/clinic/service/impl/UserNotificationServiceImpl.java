package com.example.clinic.service.impl;

import com.example.clinic.dto.response.NotificationResponse;
import com.example.clinic.entity.Notification;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.NotificationRepository;
import com.example.clinic.service.UserNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserNotificationServiceImpl implements UserNotificationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<Notification> result = unreadOnly
                ? notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return result.map(NotificationResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    @Transactional
    public void markRead(Long userId, Long notificationId) {
        // Không phải của user này thì trả 404 như không tồn tại (không lộ id của người khác)
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy thông báo với id: " + notificationId));
        n.setRead(true);
    }

    @Override
    @Transactional
    public int markAllRead(Long userId) {
        return notificationRepository.markAllReadByUserId(userId);
    }
}
