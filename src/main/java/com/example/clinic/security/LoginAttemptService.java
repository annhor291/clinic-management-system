package com.example.clinic.security;

import com.example.clinic.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Chặn dò mật khẩu: đếm số lần đăng nhập SAI theo (IP + email) và theo IP trong một cửa sổ trượt.
// Không dùng cờ locked của User để tránh kẻ xấu cố ý làm sai để khóa tài khoản người khác.
// Lưu trong bộ nhớ nên chỉ đúng với 1 máy chủ.
@Component
public class LoginAttemptService {

    private final int maxPerAccount;
    private final int maxPerIp;
    private final long windowMillis;

    private final Map<String, Deque<Long>> byAccount = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> byIp = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${auth.login.max-failures-per-account:5}") int maxPerAccount,
            @Value("${auth.login.max-failures-per-ip:30}") int maxPerIp,
            @Value("${auth.login.window-minutes:15}") int windowMinutes) {
        this.maxPerAccount = maxPerAccount;
        this.maxPerIp = maxPerIp;
        this.windowMillis = windowMinutes * 60_000L;
    }

    // Gọi TRƯỚC khi xác thực: ném 429 nếu đang bị chặn
    public void checkAllowed(String ip, String email) {
        long now = System.currentTimeMillis();
        long wait = Math.max(
                waitSeconds(byAccount, accountKey(ip, email), maxPerAccount, now),
                waitSeconds(byIp, ip, maxPerIp, now));
        if (wait > 0) {
            throw new TooManyRequestsException(
                    "Bạn đã nhập sai quá nhiều lần, vui lòng thử lại sau " + wait + " giây", wait);
        }
    }

    public void recordFailure(String ip, String email) {
        long now = System.currentTimeMillis();
        add(byAccount, accountKey(ip, email), now);
        add(byIp, ip, now);
    }

    public void recordSuccess(String ip, String email) {
        byAccount.remove(accountKey(ip, email));
    }

    private String accountKey(String ip, String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return ip + "|" + normalized;
    }

    private void add(Map<String, Deque<Long>> map, String key, long now) {
        Deque<Long> window = map.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            prune(window, now);
            window.addLast(now);
        }
    }

    private long waitSeconds(Map<String, Deque<Long>> map, String key, int max, long now) {
        Deque<Long> window = map.get(key);
        if (window == null) return 0;
        synchronized (window) {
            prune(window, now);
            if (window.size() < max) return 0;
            long remainingMillis = windowMillis - (now - window.peekFirst());
            return Math.max(1, (remainingMillis + 999) / 1000);
        }
    }

    private void prune(Deque<Long> window, long now) {
        while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
            window.pollFirst();
        }
    }

    // Dọn các khóa không còn hoạt động để map không phình mãi
    @Scheduled(fixedDelay = 300_000)
    public void evictIdle() {
        long now = System.currentTimeMillis();
        evict(byAccount, now);
        evict(byIp, now);
    }

    private void evict(Map<String, Deque<Long>> map, long now) {
        map.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last >= windowMillis;
            }
        });
    }
}
