package com.example.clinic.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimiterTest {

    @Test
    void allowsUpToMax_thenReturnsWaitSeconds() {
        var limiter = new RateLimiter(2, 60);

        assertThat(limiter.tryAcquire("k")).isZero();
        assertThat(limiter.tryAcquire("k")).isZero();
        assertThat(limiter.tryAcquire("k")).isGreaterThan(0);
    }

    @Test
    void keysAreIndependent() {
        var limiter = new RateLimiter(1, 60);

        limiter.tryAcquire("a");

        assertThat(limiter.tryAcquire("b")).isZero();
    }

    @Test
    void allowsAgainAfterWindowPasses() throws Exception {
        var limiter = new RateLimiter(1, 1);

        limiter.tryAcquire("k");
        assertThat(limiter.tryAcquire("k")).isGreaterThan(0);
        Thread.sleep(1100);

        assertThat(limiter.tryAcquire("k")).isZero();
    }
}
