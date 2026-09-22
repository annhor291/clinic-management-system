package com.example.clinic.entity.enums;

public enum AuditActionType {

    // Đã wire vào UserServiceImpl ở phase này
    USER_ROLE_CHANGED,
    USER_LOCKED,
    USER_UNLOCKED,
    USER_DELETED,
    USER_PASSWORD_RESET_BY_ADMIN,

    // Chuẩn bị sẵn cho các phase sau (Payment, sửa tay lịch làm việc) — chưa wire
    PAYMENT_CREATED,
    PAYMENT_STATUS_CHANGED,
    SCHEDULE_MANUALLY_EDITED,
    SCHEDULE_MANUALLY_CANCELLED
}
