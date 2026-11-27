package com.example.clinic.entity.enums;

public enum AuditActionType {

    // Đã wire vào UserServiceImpl ở phase này
    USER_ROLE_CHANGED,
    USER_LOCKED,
    USER_UNLOCKED,
    USER_DELETED,
    USER_PASSWORD_RESET_BY_ADMIN,
    PAYMENT_CREATED,
    PAYMENT_STATUS_CHANGED,
    SCHEDULE_MANUALLY_EDITED,
    SCHEDULE_MANUALLY_CANCELLED
}
