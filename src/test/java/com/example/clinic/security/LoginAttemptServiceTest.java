package com.example.clinic.security;

import com.example.clinic.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

public class LoginAttemptServiceTest {

    @Test
    void blocksAccount_afterTooManyFailures() {
        var service = new LoginAttemptService(3, 100, 15);
        for (int i = 0; i < 3; i++) service.recordFailure("1.1.1.1", "a@x.com");

        assertThatThrownBy(() -> service.checkAllowed("1.1.1.1", "a@x.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isGreaterThan(0));
    }

    @Test
    void emailIsCaseInsensitive_andOtherEmailNotBlocked() {
        var service = new LoginAttemptService(2, 100, 15);
        service.recordFailure("1.1.1.1", "A@x.com");
        service.recordFailure("1.1.1.1", "a@X.com");

        assertThatThrownBy(() -> service.checkAllowed("1.1.1.1", "a@x.com"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> service.checkAllowed("1.1.1.1", "b@x.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void successClearsAccountCounter() {
        var service = new LoginAttemptService(2, 100, 15);
        service.recordFailure("1.1.1.1", "a@x.com");
        service.recordSuccess("1.1.1.1", "a@x.com");
        service.recordFailure("1.1.1.1", "a@x.com");

        assertThatCode(() -> service.checkAllowed("1.1.1.1", "a@x.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void blocksWholeIp_whenTryingManyAccounts() {
        var service = new LoginAttemptService(100, 3, 15);
        service.recordFailure("1.1.1.1", "a@x.com");
        service.recordFailure("1.1.1.1", "b@x.com");
        service.recordFailure("1.1.1.1", "c@x.com");

        assertThatThrownBy(() -> service.checkAllowed("1.1.1.1", "d@x.com"))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> service.checkAllowed("2.2.2.2", "d@x.com"))
                .doesNotThrowAnyException();
    }
}
