package com.example.clinic.dto.response;

import com.example.clinic.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse (
        Long id,
        String type,
        String title,
        String content,
        Long appointmentId,
        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getContent(),
                n.getAppointment() != null ? n.getAppointment().getId() : null,
                n.isRead(),
                n.getCreatedAt()
        );
    }
}

