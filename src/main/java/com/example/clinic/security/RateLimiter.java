package com.example.clinic.security;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Giới hạn tốc độ theo cửa sổ trượt: mỗi key tối đa `max` lần trong `windowSeconds` giây.
// Lưu trong bộ nhớ nên chỉ đúng với 1 máy chủ.
public class RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int max;
    private final long windowMillis;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int max, int windowSeconds) {
        this.max = max;
        this.windowMillis = windowSeconds * 1000L;
    }

    // Trả 0 nếu được phép (và ghi nhận lượt này); trả số giây phải chờ nếu đã vượt giới hạn.
    public long tryAcquire(String key) {
        long now = System.currentTimeMillis();
        if (hits.size() > CLEANUP_THRESHOLD) {
            evictIdle(now);
        }
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() >= windowMillis) {
                window.pollFirst();
            }
            if (window.size() >= max) {
                long remainingMillis = windowMillis - (now - window.peekFirst());
                return Math.max(1, (remainingMillis + 999) / 1000);
            }
            window.addLast(now);
            return 0;
        }
    }

    private void evictIdle(long now) {
        hits.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long last = e.getValue().peekLast();
                return last == null || now - last >= windowMillis;
            }
        });
    }
}
