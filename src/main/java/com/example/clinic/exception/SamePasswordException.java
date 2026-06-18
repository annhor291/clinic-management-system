package com.example.clinic.exception;

// Ném ra khi mật khẩu mới giống mật khẩu cũ
// GlobalExceptionHandler sẽ bắt và trả về HTTP 400 Bad Request
public class SamePasswordException extends RuntimeException{
    public SamePasswordException(String message) {
        super(message);
    }
}
