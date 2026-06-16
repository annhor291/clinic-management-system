package com.example.clinic.repository;

import com.example.clinic.entity.RefreshToken;
import com.example.clinic.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Tìm token khi client gửi lên để validate
    Optional<RefreshToken> findByToken(String token);

    // Revoke tất cả token của user khi logout hoặc đổi mật khẩu
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user")
    void revokeAllByUser(User user);

    // Xóa tất cả token hết hạn — dùng cho scheduled cleanup
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < CURRENT_TIMESTAMP")
    void deleteAllExpired();
}
