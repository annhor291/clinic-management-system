package com.example.clinic.notification.event;

// Phát khi tài khoản sẵn sàng lần đầu (xác minh email xong hoặc đăng nhập Google lần đầu)
public record UserWelcomeEvent(Long userId) {
}
