package com.example.clinic.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    // true = thành công, false = thất bại
    private boolean success;

    // Thông báo kết quả
    private String message;

    // Dữ liệu trả về (null nếu thất bại)
    private T data;

    // Thời điểm response
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // ===== Static factory methods =====

    // Tạo response thành công có data
    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // Tạo response thành công không có data (VD: xoá thành công)
    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .build();
    }

    // Tạo response thất bại
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .build();
    }
}
