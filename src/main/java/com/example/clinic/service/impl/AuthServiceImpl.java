package com.example.clinic.service.impl;

import com.example.clinic.dto.request.LoginRequest;
import com.example.clinic.dto.request.RegisterRequest;
import com.example.clinic.dto.response.AuthResponse;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.JwtUtil;
import com.example.clinic.service.AuthService;
import com.example.clinic.service.GoogleAuthService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtils;
    private final AuthenticationManager authenticationManager;
    private final GoogleAuthService googleAuthService;

    // Đăng ký tài khoản mới
    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Kiểm tra email đã tồn tại chưa
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Email '" + request.getEmail() + "' đã được sử dụng");
        }

        // Kiểm tra username đã tồn tại chưa
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException(
                    "Username '" + request.getUsername() + "' đã được sử dụng");
        }

        // Tạo User mới với password đã mã hóa BCrypt
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        // Tạo JWT token cho user vừa đăng ký
        String token = jwtUtils.generateToken(savedUser);

        // Lấy profileId (patientId hoặc doctorId) nếu có
        Long profileId = getProfileId(savedUser);

        return AuthResponse.builder()
                .token(token)
                .userId(savedUser.getId())
                .username(savedUser.getUsername())
                .email(savedUser.getEmail())
                .role(savedUser.getRole())
                .profileId(profileId)
                .build();
    }

    // Đăng nhập và trả về JWT token
    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        // AuthenticationManager tự động:
        // 1. Load user theo email (gọi UserDetailsServiceImpl)
        // 2. So sánh password với BCrypt
        // 3. Ném exception nếu sai
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        // Lấy User từ Authentication
        User user = (User) authentication.getPrincipal();

        // Tạo JWT token
        String token = jwtUtils.generateToken(user);

        // Lấy profileId
        Long profileId = getProfileId(user);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .profileId(profileId)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleIdToken.Payload payload = googleAuthService.verifyToken(idToken);

        String email = payload.getEmail();

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {

                    User newUser = User.builder()
                            .username(email)
                            .email(email)
                            .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                            .role(Role.PATIENT)
                            .provider(AuthProvider.GOOGLE)
                            .enabled(true)
                            .build();
                    return userRepository.save(newUser);
                });

        if (user.getRole() != Role.PATIENT) {
            throw new IllegalArgumentException("Chỉ Bệnh nhân đợc phép đăng nhập với Google");
        }

        String token = jwtUtils.generateToken(user);

        Long profileId = getProfileId(user);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .profileId(profileId)
                .build();
    }

    // ===== Private helper =====

    // Lấy profileId tương ứng với role của user
    // PATIENT → patientId, DOCTOR → doctorId, ADMIN/RECEPTIONIST → null
    private Long getProfileId(User user) {
        if (user.getRole() == Role.PATIENT) {
            return patientRepository.findByUserId(user.getId())
                    .map(p -> p.getId())
                    .orElse(null);
        } else if (user.getRole() == Role.DOCTOR) {
            return doctorRepository.findByUserId(user.getId())
                    .map(d -> d.getId())
                    .orElse(null);
        }
        return null;
    }
}
