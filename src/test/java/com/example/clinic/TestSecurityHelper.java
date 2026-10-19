package com.example.clinic;

import com.example.clinic.entity.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

// Giả lập 1 user đã đăng nhập trong Integration Test — set thẳng Authentication vào
// SecurityContextHolder, bỏ qua hoàn toàn luồng JWT/HTTP thật (vì ta đang test tầng
// Service/Repository, không phải test tầng Controller/HTTP).
// GIẢ ĐỊNH: SecurityUtil đọc principal là chính đối tượng User (không phải JWT claims).
// Nếu SecurityUtil thực tế đọc khác, cần chỉnh lại class này.
public class TestSecurityHelper {

    public static void loginAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
