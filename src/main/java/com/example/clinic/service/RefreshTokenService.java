package com.example.clinic.service;

import com.example.clinic.entity.RefreshToken;
import com.example.clinic.entity.User;

public interface RefreshTokenService {

    // Tạo refresh token mới cho user (mỗi lần login 1 thiết bị)
    RefreshToken createRefreshToken(User user);

    // Validate refresh token client gửi lên
    RefreshToken validateRefreshToken(String token);

    // Revoke tất cả token của user (logout, đổi mật khẩu)
    void revokeAllUserTokens(User user);

    // Xóa token hết hạn (scheduled cleanup)
    void deleteExpiredTokens();
}
