package com.example.clinic.notification.event;

import com.example.clinic.dto.response.NotificationResponse;

public record NotificationCreatedEvent (Long userId, NotificationResponse notification){
}
