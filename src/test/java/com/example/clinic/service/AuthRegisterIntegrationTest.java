package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.dto.request.LoginRequest;
import com.example.clinic.dto.request.RegisterRequest;
import com.example.clinic.dto.response.AuthResponse;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void register_newEmail_createsUnverifiedPatient_andSendsVerificationMail() {
        authService.register(RegisterRequest.builder()
                .email("new_patient@example.com").password("secret123").build());

        User u = userRepository.findByEmail("new_patient@example.com").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.PATIENT);
        assertThat(u.getDisplayName()).isEqualTo("new_patient@example.com");
        assertThat(u.isEmailVerified()).isFalse();
        assertThat(u.getVerificationToken()).isNotBlank();
        verify(emailService).sendEmail(eq("new_patient@example.com"), any(), any());
    }

    @Test
    void register_existingVerifiedEmail_doesNotThrow_andDoesNotChangeAccount() {
        userRepository.save(User.builder()
                .username("taken@example.com").email("taken@example.com").password("oldhash")
                .role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());

        authService.register(RegisterRequest.builder()
                .email("taken@example.com").password("secret123").build());

        User u = userRepository.findByEmail("taken@example.com").orElseThrow();
        assertThat(u.getPassword()).isEqualTo("oldhash");
        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getVerificationToken()).isNull();
        verify(emailService).sendEmail(eq("taken@example.com"), any(), any());
    }

    @Test
    void register_pendingEmail_isRefreshed_notBlocked() {
        authService.register(RegisterRequest.builder()
                .email("pending@example.com").password("secret123").build());
        String firstToken = userRepository.findByEmail("pending@example.com").orElseThrow().getVerificationToken();

        authService.register(RegisterRequest.builder()
                .email("pending@example.com").password("secret456").build());

        User u = userRepository.findByEmail("pending@example.com").orElseThrow();
        assertThat(u.getVerificationToken()).isNotEqualTo(firstToken);
        assertThat(u.isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmail_validToken_marksVerified() {
        authService.register(RegisterRequest.builder()
                .email("v_user@example.com").password("secret123").build());
        String token = userRepository.findByEmail("v_user@example.com").orElseThrow().getVerificationToken();

        authService.verifyEmail(token);

        User u = userRepository.findByEmail("v_user@example.com").orElseThrow();
        assertThat(u.isEmailVerified()).isTrue();
        assertThat(u.getVerificationToken()).isNull();
    }

    @Test
    void verifyEmail_unknownToken_throws() {
        assertThatThrownBy(() -> authService.verifyEmail("khong-co-token"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void login_unverifiedAccount_withCorrectPassword_isRejected() {
        authService.register(RegisterRequest.builder()
                .email("nv_user@example.com").password("secret123").build());

        // tạo LoginRequest theo cách bạn đã dùng trong AuthLoginLockedAccountIntegrationTest
        assertThatThrownBy(() -> authService.login(
                LoginRequest.builder().email("nv_user@example.com").password("secret123").build()))
                .isInstanceOf(AccessDeniedException.class);
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

