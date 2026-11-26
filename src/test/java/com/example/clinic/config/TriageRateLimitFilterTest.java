package com.example.clinic.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class TriageRateLimitFilterTest {

    private MockHttpServletResponse call(TriageRateLimitFilter filter, String method,
                                         String uri, String ip) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, uri);
        req.setRemoteAddr(ip);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    @Test
    void blocksAfterLimit_withRetryAfter() throws Exception {
        var filter = new TriageRateLimitFilter(3, 60);

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "POST", "/api/v1/triage/next", "1.1.1.1").getStatus())
                    .isEqualTo(200);
        }
        var blocked = call(filter, "POST", "/api/v1/triage/next", "1.1.1.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void limitIsPerIp() throws Exception {
        var filter = new TriageRateLimitFilter(1, 60);

        call(filter, "POST", "/api/v1/triage/next", "1.1.1.1");

        assertThat(call(filter, "POST", "/api/v1/triage/next", "2.2.2.2").getStatus())
                .isEqualTo(200);
    }

    @Test
    void otherEndpointsAreNotLimited() throws Exception {
        var filter = new TriageRateLimitFilter(1, 60);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "GET", "/api/v1/clinic", "1.1.1.1").getStatus())
                    .isEqualTo(200);
        }
    }
}

