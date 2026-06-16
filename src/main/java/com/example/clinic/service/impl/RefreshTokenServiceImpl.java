package com.example.clinic.service.impl;

import com.example.clinic.entity.RefreshToken;
import com.example.clinic.entity.User;
import com.example.clinic.exception.InvalidRefreshTokenException;
import com.example.clinic.repository.RefreshTokenRepository;
import com.example.clinic.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration; // milliseconds

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .user(user)
                .expiryDate(LocalDateTime.now()
                        .plusSeconds(refreshExpiration / 1000))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken validateRefreshToken(String token) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token không tồn tại"));

        if (!refreshToken.isValid()) {
            throw new InvalidRefreshTokenException("Refresh token đã hết hạn hoặc bị thu hồi");
        }

        return refreshToken;
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }

    @Override
    @Transactional
    @Scheduled(cron = "0 0 3 * * *") // Chạy lúc 3 giờ sáng mỗi ngày
    public void deleteExpiredTokens() {
        refreshTokenRepository.deleteAllExpired();
    }
}
