package com.example.clinic.controller;

import com.example.clinic.dto.request.BroadcastNotificationRequest;
import com.example.clinic.dto.response.NotificationResponse;
import com.example.clinic.entity.User;
import com.example.clinic.service.NotificationService;
import com.example.clinic.service.UserNotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final UserNotificationService userNotificationService;
    private final NotificationService notificationService;

    @GetMapping
    public PagedModel<NotificationResponse> list(
            @AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return new PagedModel<>(userNotificationService.list(user.getId(), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal User user) {
        return Map.of("count", userNotificationService.countUnread(user.getId()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        userNotificationService.markRead(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal User user) {
        return Map.of("updated", userNotificationService.markAllRead(user.getId()));
    }

    // POST /api/v1/notifications/broadcast — ADMIN gửi thông báo chung (trong app)
    @PostMapping("/broadcast")
    public Map<String, Integer> broadcast(@Valid @RequestBody BroadcastNotificationRequest request) {
        int recipients = notificationService.broadcastGeneral(
                request.getTitle(), request.getContent(), request.getTargetRole());
        return Map.of("recipients", recipients);
    }
}
