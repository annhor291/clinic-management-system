package com.example.clinic.exception;

// Ném ra khi mật khẩu xác nhận không khớp với mật khẩu mới
// GlobalExceptionHandler sẽ bắt và trả về HTTP 400 Bad Request
public class PasswordMismatchException extends RuntimeException{
    public PasswordMismatchException(String message) {
        super(message);
    }
}
