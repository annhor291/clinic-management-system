package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ForgotPasswordRateLimitIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired private UserRepository userRepository;
    @MockitoBean
    private EmailService emailService;

    @Test
    void onlyThreeResetEmailsPerAddress_butNeverAnError() {
        userRepository.save(User.builder().username("fp_user").email("fp@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());

        for (int i = 0; i < 6; i++) {
            authService.forgotPassword("fp@example.com");   // không được ném lỗi dù vượt giới hạn
        }

        verify(emailService, times(3)).sendForgotPasswordEmail(any(), any());
    }
}
