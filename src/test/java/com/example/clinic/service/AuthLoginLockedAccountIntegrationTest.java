package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.dto.request.LoginRequest;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AuthLoginLockedAccountIntegrationTest extends BaseIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired
    private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.save(User.builder().username("locked_user").email("locked@example.com")
                .password(passwordEncoder.encode("Correct@123")).role(Role.PATIENT)
                .provider(AuthProvider.LOCAL).enabled(true).locked(true).build());
    }

    private LoginRequest login(String password) {
        LoginRequest request = new LoginRequest();
        request.setEmail("locked@example.com");
        request.setPassword(password);
        return request;
    }

    @Test
    void lockedAccount_wrongPassword_looksLikeNormalBadCredentials() {
        assertThatThrownBy(() -> authService.login(login("Wrong@123")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void lockedAccount_correctPassword_revealsLockedStatus() {
        assertThatThrownBy(() -> authService.login(login("Correct@123")))
                .isInstanceOf(LockedException.class);
    }
}
