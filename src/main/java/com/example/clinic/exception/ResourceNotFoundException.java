package com.example.clinic.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Ném ra khi không tìm thấy resource trong DB
// @ResponseStatus: Spring tự động trả về HTTP 404 khi exception này bị ném
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
