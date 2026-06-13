package com.example.clinic.service;


import com.example.clinic.dto.request.LoginRequest;
import com.example.clinic.dto.request.RegisterRequest;
import com.example.clinic.dto.response.AuthResponse;

public interface AuthService {
    // Đăng ký tài khoản mới
    AuthResponse register(RegisterRequest request);

    // Đăng nhập và trả về JWT token
    AuthResponse login(LoginRequest request);

    // Đăng nhập bằng google
    AuthResponse loginWithGoogle(String idToken);
}
