package com.example.clinic.service.impl;

import com.example.clinic.dto.request.LoginRequest;
import com.example.clinic.dto.request.RegisterRequest;
import com.example.clinic.dto.response.AuthResponse;
import com.example.clinic.entity.RefreshToken;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.exception.*;
import com.example.clinic.notification.event.UserWelcomeEvent;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.JwtUtil;
import com.example.clinic.security.RateLimiter;
import com.example.clinic.service.AuthService;
import com.example.clinic.service.EmailService;
import com.example.clinic.service.GoogleAuthService;
import com.example.clinic.service.RefreshTokenService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;
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
    private final RefreshTokenService refreshTokenService;
    private final EmailService emailService;
    private final ApplicationEventPublisher eventPublisher;
    // Tối đa 3 thư đặt lại mật khẩu / 15 phút cho mỗi email — chống gửi thư rác vào hộp thư người khác
    private final RateLimiter forgotPasswordPerEmail = new RateLimiter(3, 15 * 60);
    private final RateLimiter registerPerEmail = new RateLimiter(3, 60 * 60);

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    // Đăng ký tài khoản mới
    @Override
    @Transactional
    public void register(RegisterRequest request) {
        String email = request.getEmail();

        // Mã hóa ở MỌI nhánh để thời gian phản hồi không lộ email có tồn tại hay không
        String hash = passwordEncoder.encode(request.getPassword());

        // Giới hạn một lần cho mọi nhánh: vượt thì im lặng bỏ qua (vẫn trả 202), không đổi gì cả
        String key = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (registerPerEmail.tryAcquire(key) > 0) {
            return;
        }

        User existing = userRepository.findByEmail(email).orElse(null);

        if (existing == null) {
            User user = User.builder()
                    .username(email)   // đổi thành họ tên khi bệnh nhân hoàn thiện hồ sơ
                    .email(email)
                    .password(hash)
                    .role(Role.PATIENT)
                    .provider(AuthProvider.LOCAL)
                    .enabled(true)
                    .emailVerified(false)
                    .build();
            sendVerification(user);
            return;
        }

        if (existing.getProvider() == AuthProvider.LOCAL && !existing.isEmailVerified()) {
            // Đăng ký dở: cho đăng ký lại (ghi đè mật khẩu), để không ai "chiếm chỗ" email người khác
            existing.setPassword(hash);
            sendVerification(existing);
            return;
        }

        // Email đã có tài khoản thật: không đổi gì, chỉ báo cho CHỦ email
        emailService.sendEmail(email, "Thông báo đăng ký - Clinic Management System", """
            <p>Có người vừa dùng email này để đăng ký tài khoản.</p>
            <p>Nếu là bạn, email này đã có tài khoản: hãy đăng nhập, hoặc dùng "Quên mật khẩu" nếu bạn không nhớ mật khẩu.</p>
            <p>Nếu không phải bạn, hãy bỏ qua email này.</p>
            """);
    }

    private void sendVerification(User user) {
        String token = UUID.randomUUID().toString();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        String link = frontendUrl + "/verify-email?token=" + token;
        emailService.sendEmail(user.getEmail(), "Xác minh email - Clinic Management System", """
            <h2>Xác minh email</h2>
            <p>Bấm vào liên kết dưới đây để hoàn tất đăng ký (có hiệu lực 24 giờ):</p>
            <p><a href="%s">Xác minh email</a></p>
            <p>Nếu bạn không đăng ký, hãy bỏ qua email này.</p>
            """.formatted(link));
    }

    @Override
    @Transactional
    public void verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Liên kết xác minh không hợp lệ hoặc đã được sử dụng"));

        if (user.getVerificationTokenExpiry() == null
                || user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException(
                    "Liên kết xác minh đã hết hạn. Vui lòng đăng ký lại để nhận liên kết mới.");
        }
        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);
        eventPublisher.publishEvent(new UserWelcomeEvent(user.getId()));
    }

    // Đăng nhập và trả về JWT token
    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // AuthenticationManager tự động:
        // 1. Load user theo email (gọi UserDetailsServiceImpl)
        // 2. So sánh password với BCrypt
        // 3. Ném exception nếu sai
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (LockedException | DisabledException e) {
            // Spring kiểm tra trạng thái khóa TRƯỚC khi so mật khẩu. Nếu cứ báo "bị khóa" ngay,
            // kẻ lạ chỉ cần biết email là dò được tài khoản nào đang bị khóa.
            // Vì vậy chỉ báo khi mật khẩu đúng; mật khẩu sai thì coi như đăng nhập sai bình thường.
            boolean passwordCorrect = userRepository.findByEmail(request.getEmail())
                    .map(u -> passwordEncoder.matches(request.getPassword(), u.getPassword()))
                    .orElse(false);
            if (!passwordCorrect) {
                throw new BadCredentialsException("Bad credentials");
            }
            throw e;
        }

        // Lấy User từ Authentication
        User user = (User) authentication.getPrincipal();

        if (!user.isEmailVerified()) {
            throw new AccessDeniedException(
                    "Email của bạn chưa được xác minh. Vui lòng kiểm tra hộp thư (kể cả thư rác) và bấm vào liên kết xác minh.");
        }

        // Tạo JWT token
        String token = jwtUtils.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // Lấy profileId
        Long profileId = getProfileId(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .username(user.getDisplayName())
                .email(user.getEmail())
                .role(user.getRole())
                .profileId(profileId)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleIdToken.Payload payload = googleAuthService.verifyToken(idToken);

        if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new BadCredentialsException("Email Google chưa được xác minh");
        }

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
                    User created = userRepository.save(newUser);
                    eventPublisher.publishEvent(new UserWelcomeEvent(created.getId()));
                    return created;
                });

        // Kiểm tra provider trước khi cho login Google
        if (user.getProvider() != AuthProvider.GOOGLE) {
            throw new IllegalStateException(
                    "Email này đã được đăng ký bằng tài khoản thường. Vui lòng đăng nhập bằng mật khẩu.");
        }

        // Kiểm tra role
        if (user.getRole() != Role.PATIENT) {
            throw new IllegalStateException("Chỉ Bệnh nhân được phép đăng nhập với Google");
        }

        if (!user.isEnabled()) {
            throw new DisabledException("Tài khoản đã bị vô hiệu hóa");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Tài khoản đang bị khóa");
        }

        String token = jwtUtils.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        Long profileId = getProfileId(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .username(user.getDisplayName())
                .email(user.getEmail())
                .role(user.getRole())
                .profileId(profileId)
                .build();
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String refreshToken) {
        RefreshToken existingToken = refreshTokenService.validateRefreshToken(refreshToken);

        User user = existingToken.getUser();

        // Revoke token cũ
        refreshTokenService.revokeAllUserTokens(user);

        // Tạo access token và refresh token mới
        String newAccessToken = jwtUtils.generateToken(user);
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);

        Long profileId = getProfileId(user);

        return AuthResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .userId(user.getId())
                .username(user.getDisplayName())
                .email(user.getEmail())
                .role(user.getRole())
                .profileId(profileId)
                .build();
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        RefreshToken existingToken = refreshTokenService.validateRefreshToken(refreshToken);
        refreshTokenService.revokeAllUserTokens(existingToken.getUser());
    }

    /*@Override
    @Transactional
    public void forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Email không tồn tại trong hệ thống"));

        // Không cho phép Google account đặt lại mật khẩu
        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new IllegalStateException("Tài khoản Google không thể đặt lại mật khẩu. Vui lòng đăng nhập bằng Google.");
        }

        // Tạo reset token
        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);

        // Gửi email
        emailService.sendForgotPasswordEmail(email, resetToken);
    }*/

    @Override
    @Transactional
    public void forgotPassword(String email) {
        // Vượt giới hạn thì im lặng bỏ qua (vẫn trả 200) để không lộ email nào có tài khoản
        String key = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (forgotPasswordPerEmail.tryAcquire(key) > 0) {
            return;
        }
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || user.getProvider() == AuthProvider.GOOGLE) {
            return; // im lặng, không lộ thông tin
        }
        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(15));
        userRepository.save(user);
        emailService.sendForgotPasswordEmail(email, resetToken);
    }

    @Override
    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        User user = userRepository.findByResetToken(resetToken)
                .orElseThrow(() -> new ResourceNotFoundException("Reset token không hợp lệ"));

        // Kiểm tra token còn hạn không
        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Reset token đã hết hạn. Vui lòng yêu cầu lại.");
        }

        // Cập nhật password mới
        user.setPassword(passwordEncoder.encode(newPassword));

        // Xóa reset token sau khi dùng
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        // Revoke tất cả refresh token → buộc đăng nhập lại
        refreshTokenService.revokeAllUserTokens(user);

        userRepository.save(user);

    }

    @Override
    @Transactional
    public void changePassword(String email, String oldPassword, String newPassword, String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            throw new PasswordMismatchException("Mật khẩu xác nhận không khớp");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user"));

        if (user.getProvider() == AuthProvider.GOOGLE) {
            throw new IllegalStateException("Tài khoản Google không thể đổi mật khẩu");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new WrongPasswordException("Mật khẩu cũ không đúng");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new SamePasswordException("Mật khẩu mới không được giống mật khẩu cũ");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        refreshTokenService.revokeAllUserTokens(user);
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
