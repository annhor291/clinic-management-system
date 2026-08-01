package com.example.clinic.booking.engine;

// Ném ra khi một Booking Rule bị vi phạm.
// Kế thừa IllegalStateException để tái sử dụng handler có sẵn trong GlobalExceptionHandler
// (đã map IllegalStateException → HTTP 400), không cần sửa GlobalExceptionHandler.
public class BookingRuleViolationException extends IllegalStateException {
    public BookingRuleViolationException(String message) {
        super(message);
    }
}

