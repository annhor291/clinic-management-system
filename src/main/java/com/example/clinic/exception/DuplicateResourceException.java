package com.example.clinic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Ném ra khi dữ liệu bị trùng lặp (VD: email, username, số BHYT...)
// @ResponseStatus: Spring tự động trả về HTTP 409 Conflict
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateResourceException extends RuntimeException{

    public DuplicateResourceException(String message) {
        super(message);
    }
}
