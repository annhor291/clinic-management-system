package com.example.clinic.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Giới hạn tốc độ gọi cho endpoint công khai POST /api/v1/triage/next, tính theo IP.
// Cửa sổ trượt: mỗi IP chỉ được gọi tối đa maxRequests lần trong windowSeconds giây.
// Lưu trong bộ nhớ nên chỉ đúng với 1 máy chủ (đủ cho phạm vi đồ án).
@Component
public class TriageRateLimitFilter extends OncePerRequestFilter {

    private static final String TARGET_PATH = "/api/v1/triage/next";

    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public TriageRateLimitFilter(
            @Value("${triage.rate-limit.max-requests:60}") int maxRequests,
            @Value("${triage.rate-limit.window-seconds:60}") int windowSeconds) {
        this.maxRequests = maxRequests;
        this.windowMillis = windowSeconds * 1000L;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equalsIgnoreCase(request.getMethod())
                && TARGET_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Chỉ dùng getRemoteAddr(): không tin header X-Forwarded-For vì client tự giả được.
        // Khi chạy sau proxy, cấu hình server.forward-headers-strategy để Spring tự điền đúng IP.
        String ip = request.getRemoteAddr();
        long now = System.currentTimeMillis();

        boolean allowed;
        long retryAfterSeconds = 1;
        Deque<Long> window = hits.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
                window.pollFirst();
            }
            if (window.size() >= maxRequests) {
                allowed = false;
                retryAfterSeconds = Math.max(1, (windowMillis - (now - window.peekFirst()) + 999) / 1000);
            } else {
                window.addLast(now);
                allowed = true;
            }
        }

        if (!allowed) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("application/json;charset=UTF-8");
            response.getOutputStream().write(
                    ("{\"success\":false,\"message\":\"Bạn thao tác quá nhanh, vui lòng thử lại sau "
                            + retryAfterSeconds + " giây\"}").getBytes(StandardCharsets.UTF_8));
            return;
        }
        chain.doFilter(request, response);
    }

    // Dọn các IP không còn hoạt động để map không phình mãi
    @Scheduled(fixedDelay = 300_000)
    public void evictIdle() {
        long now = System.currentTimeMillis();
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last >= windowMillis;
            }
        });
    }
}
