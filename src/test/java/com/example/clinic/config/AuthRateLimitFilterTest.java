package com.example.clinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthRateLimitFilterTest {

    private MockHttpServletResponse call(AuthRateLimitFilter filter, String method,
                                         String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void register_blockedAfterLimit() throws Exception {
        var filter = new AuthRateLimitFilter(2, 60, 5, 15);

        call(filter, "POST", "/api/v1/auth/register", "1.1.1.1");
        call(filter, "POST", "/api/v1/auth/register", "1.1.1.1");
        var blocked = call(filter, "POST", "/api/v1/auth/register", "1.1.1.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void forgotPassword_hasItsOwnLimit() throws Exception {
        var filter = new AuthRateLimitFilter(1, 60, 1, 15);

        call(filter, "POST", "/api/v1/auth/register", "1.1.1.1");

        assertThat(call(filter, "POST", "/api/v1/auth/forgot-password", "1.1.1.1").getStatus())
                .isEqualTo(200);
        assertThat(call(filter, "POST", "/api/v1/auth/forgot-password", "1.1.1.1").getStatus())
                .isEqualTo(429);
    }

    @Test
    void otherAuthEndpointsAreNotLimited() throws Exception {
        var filter = new AuthRateLimitFilter(1, 60, 1, 15);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "POST", "/api/v1/auth/login", "1.1.1.1").getStatus())
                    .isEqualTo(200);
        }
    }
}
