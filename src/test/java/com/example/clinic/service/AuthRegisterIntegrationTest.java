package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.dto.request.RegisterRequest;
import com.example.clinic.dto.response.AuthResponse;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class AuthRegisterIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private EmailService emailService;

    @Test
    void register_shouldAlwaysCreatePatient_andReturnRefreshToken() {
        RegisterRequest req = RegisterRequest.builder()
                .username("new_patient").email("new_patient@example.com").password("secret123").build();

        AuthResponse res = authService.register(req);

        assertThat(res.getRole()).isEqualTo(Role.PATIENT);
        assertThat(res.getToken()).isNotBlank();
        assertThat(res.getRefreshToken()).isNotBlank();
        assertThat(userRepository.findByEmail("new_patient@example.com").orElseThrow().getRole())
                .isEqualTo(Role.PATIENT);
    }

    @Test
    void forgotPassword_unknownEmail_shouldNotThrow_andNotSendEmail() {
        authService.forgotPassword("nobody@example.com");

        verify(emailService, never()).sendForgotPasswordEmail(any(), any());
    }

    @Test
    void forgotPassword_knownEmail_shouldSendEmail() {
        userRepository.save(User.builder()
                .username("known_user").email("known_user@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());

        authService.forgotPassword("known_user@example.com");

        verify(emailService).sendForgotPasswordEmail(eq("known_user@example.com"), any());
    }
}

