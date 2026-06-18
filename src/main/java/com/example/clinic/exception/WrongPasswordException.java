package com.example.clinic.exception;

// Ném ra khi mật khẩu cũ không khớp khi đổi mật khẩu
// GlobalExceptionHandler sẽ bắt và trả về HTTP 401 Unauthorized
public class WrongPasswordException extends RuntimeException  {
    public WrongPasswordException(String message) {
        super(message);
    }
}
