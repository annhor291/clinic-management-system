package com.example.clinic.exception;

import com.example.clinic.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

// @RestControllerAdvice: bắt tất cả exception từ mọi Controller
// Thay vì để lỗi trả về mặc định của Spring
// → tất cả lỗi đều được format theo ApiResponse cho thống nhất
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt lỗi không tìm thấy resource → HTTP 404
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi dữ liệu trùng lặp → HTTP 409
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateResourceException(
            DuplicateResourceException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi vi phạm business rule → HTTP 400
    // VD: xoá chuyên khoa còn bác sĩ, huỷ lịch đã hoàn thành...
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalStateException(
            IllegalStateException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi validation từ @Valid ở Controller → HTTP 400
    // VD: fullName để trống, phone sai định dạng...
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationException(
            MethodArgumentNotValidException ex) {

        // Gom tất cả lỗi validation vào 1 Map: field → message
        // VD: { "fullName": "Họ tên không được để trống", "phone": "Số điện thoại không hợp lệ" }
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Dữ liệu không hợp lệ")
                        .data(errors)
                        .build());
    }

    // Bắt lỗi Google token không hợp lệ → HTTP 401
    @ExceptionHandler(InvalidGoogleTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidGoogleTokenException(
            InvalidGoogleTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi refresh token không hợp lệ → HTTP 401
    @ExceptionHandler(InvalidRefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidRefreshTokenException(
            InvalidRefreshTokenException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi mật khẩu không đúng khi đổi mật khẩu
    @ExceptionHandler(WrongPasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleWrongPasswordException(
            WrongPasswordException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi mật khẩu xác nhận không khớp → HTTP 400
    @ExceptionHandler(PasswordMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handlePasswordMismatchException(
            PasswordMismatchException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi mật khẩu mới giống mật khẩu cũ → HTTP 400
    @ExceptionHandler(SamePasswordException.class)
    public ResponseEntity<ApiResponse<Void>> handleSamePasswordException(
            SamePasswordException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Body thiếu hoặc JSON sai định dạng → HTTP 400 (trước đây rơi vào handler chung và trả 500)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(
            HttpMessageNotReadableException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Dữ liệu gửi lên thiếu hoặc không đúng định dạng JSON"));
    }

    // Tham số đường dẫn / query sai kiểu (VD /doctors/abc) → HTTP 400
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Tham số '" + ex.getName() + "' không hợp lệ"));
    }

    // Gọi sai phương thức HTTP → HTTP 405
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error("Phương thức HTTP không được hỗ trợ cho đường dẫn này"));
    }

    // Đường dẫn không tồn tại → HTTP 404
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Không tìm thấy đường dẫn yêu cầu"));
    }

    // Bắt tất cả exception không mong muốn còn lại → HTTP 500
    // Tránh để lộ stack trace ra ngoài client
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Lỗi không mong muốn", ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Đã xảy ra lỗi hệ thống, vui lòng thử lại sau"));
    }

    // Bắt lỗi sai email hoặc password → HTTP 401
    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuthException(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Email hoặc mật khẩu không đúng"));
    }

    // Quá nhiều lần đăng nhập sai → HTTP 429
    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiResponse<Void>> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Tài khoản bị khóa / vô hiệu hóa → HTTP 403
    // Chỉ tới được đây khi người dùng đã nhập ĐÚNG mật khẩu (xem AuthServiceImpl.login)
    @ExceptionHandler({LockedException.class, DisabledException.class})
    public ResponseEntity<ApiResponse<Void>> handleAccountStatus(Exception ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(
                        "Tài khoản của bạn đang bị khóa hoặc đã bị vô hiệu hóa. "
                                + "Vui lòng liên hệ phòng khám để được hỗ trợ."));
    }

    // Bắt lỗi không có quyền → HTTP 403
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }
}
