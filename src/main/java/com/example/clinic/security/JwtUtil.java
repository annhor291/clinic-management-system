package com.example.clinic.security;

import com.example.clinic.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

// Utility class xử lý JWT token
// Tạo token, validate token, parse thông tin từ token
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    // Tạo SecretKey từ chuỗi secret trong application.properties
    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // Tạo JWT token từ UserDetails
    public String generateToken(UserDetails userDetails) {

        User user = (User) userDetails;

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("role", user.getRole().name());

        return buildToken(claims, user.getEmail(), expiration);
    }

    // Tạo token với claims và thời gian hết hạn
    private String buildToken(Map<String, Object> claims, String subject, long expiration) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // Lấy email (subject) từ token
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    // Lấy userId từ JWT token
    // Dùng cho ownership check mà không cần client truyền id
    public Long extractUserId(String token) {
        Object value = extractAllClaims(token).get("userId");

        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }

        if (value instanceof Long) {
            return (Long) value;
        }

        return Long.valueOf(value.toString());
    }

    // Lấy role từ JWT token
    // Dùng cho phân quyền nhanh mà không cần query DB
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    // Lấy thời gian hết hạn từ token
    public Date extractExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    // Kiểm tra token có hợp lệ không
    // Hợp lệ = email khớp + chưa hết hạn
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String email = extractEmail(token);
        return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // Kiểm tra token đã hết hạn chưa
    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // Parse tất cả claims từ token
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Validate token — dùng trong JwtAuthFilter
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (MalformedJwtException e) {
            log.error("Token không hợp lệ: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("Token đã hết hạn: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("Token không được hỗ trợ: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("Token rỗng: {}", e.getMessage());
        }
        return false;
    }
}
