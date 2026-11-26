package com.example.clinic.config;

import com.example.clinic.security.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

// Giới hạn tốc độ theo IP cho 2 endpoint công khai dễ bị lạm dụng: đăng ký và quên mật khẩu.
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final String REGISTER_PATH = "/api/v1/auth/register";
    private static final String FORGOT_PATH = "/api/v1/auth/forgot-password";

    private final RateLimiter registerLimiter;
    private final RateLimiter forgotLimiter;

    public AuthRateLimitFilter(
            @Value("${auth.register.max-per-ip:20}") int registerMax,
            @Value("${auth.register.window-minutes:60}") int registerWindowMinutes,
            @Value("${auth.forgot-password.max-per-ip:5}") int forgotMax,
            @Value("${auth.forgot-password.window-minutes:15}") int forgotWindowMinutes) {
        this.registerLimiter = new RateLimiter(registerMax, registerWindowMinutes * 60);
        this.forgotLimiter = new RateLimiter(forgotMax, forgotWindowMinutes * 60);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        return !REGISTER_PATH.equals(uri) && !FORGOT_PATH.equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Chỉ dùng getRemoteAddr(): không tin X-Forwarded-For vì client tự giả được.
        String ip = request.getRemoteAddr();
        RateLimiter limiter = REGISTER_PATH.equals(request.getRequestURI())
                ? registerLimiter : forgotLimiter;

        long wait = limiter.tryAcquire(ip);
        if (wait > 0) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(wait));
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    ("{\"success\":false,\"message\":\"Bạn thao tác quá nhanh, vui lòng thử lại sau "
                            + wait + " giây\"}").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }
}
