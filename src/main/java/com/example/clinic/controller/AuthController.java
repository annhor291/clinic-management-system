package com.example.clinic.controller;

import com.example.clinic.dto.request.GoogleLoginRequest;
import com.example.clinic.dto.request.LoginRequest;
import com.example.clinic.dto.request.RegisterRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.AuthResponse;
import com.example.clinic.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // POST /api/v1/auth/register
    // Đăng ký tài khoản mới — không cần token
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request) {

        AuthResponse response = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký thành công", response));
    }

    // POST /api/v1/auth/login
    // Đăng nhập — không cần token, trả về JWT token
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(
                ApiResponse.success("Đăng nhập thành công", response));
    }

    // POST /api/v1/auth/google
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> loginWithGoogle(
            @RequestBody GoogleLoginRequest request) {

        return ResponseEntity.ok(
                authService.loginWithGoogle(request.getIdToken()));
    }
}
