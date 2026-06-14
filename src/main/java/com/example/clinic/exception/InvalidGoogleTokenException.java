package com.example.clinic.exception;

// Ném ra khi Google ID Token không hợp lệ hoặc không thể xác thực
// GlobalExceptionHandler sẽ bắt và trả về HTTP 401 Unauthorized
public class InvalidGoogleTokenException extends RuntimeException {
    public InvalidGoogleTokenException(String message) {
        super(message);
    }
}
