package com.example.clinic.security;

import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

// Utility class để lấy thông tin user đang đăng nhập từ SecurityContext
public class SecurityUtil {

    // Lấy Authentication object từ SecurityContext
    private static Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder
                .getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("Chưa đăng nhập");
        }
        return auth;
    }

    // Lấy User entity đang đăng nhập
    public static User getCurrentUser() {
        Authentication auth = getAuthentication();
        Object principal = auth.getPrincipal();
        if (principal instanceof User) {
            return (User) principal;
        }
        throw new ResourceNotFoundException("Không tìm thấy thông tin user");
    }

    // Lấy id của user đang đăng nhập
    public static Long getCurrentUserId() {
        return getCurrentUser().getId();
    }

    // Lấy role của user đang đăng nhập
    public static Role getCurrentRole() {
        return getCurrentUser().getRole();
    }

    // Lấy email của user đang đăng nhập
    public static String getCurrentEmail() {
        return getCurrentUser().getEmail();
    }

    // Kiểm tra user hiện tại có phải ADMIN không
    public static boolean isAdmin() {
        return getCurrentRole() == Role.ADMIN;
    }

    // Kiểm tra user hiện tại có phải DOCTOR không
    public static boolean isDoctor() {
        return getCurrentRole() == Role.DOCTOR;
    }

    // Kiểm tra user hiện tại có phải PATIENT không
    public static boolean isPatient() {
        return getCurrentRole() == Role.PATIENT;
    }

    // Kiểm tra user hiện tại có phải RECEPTIONIST không
    public static boolean isReceptionist() {
        return getCurrentRole() == Role.RECEPTIONIST;
    }

    // Kiểm tra user hiện tại có phải ADMIN hoặc RECEPTIONIST không
    public static boolean isAdminOrReceptionist() {
        Role role = getCurrentRole();
        return role == Role.ADMIN || role == Role.RECEPTIONIST;
    }

    // Kiểm tra ownership — user hiện tại có quyền thao tác không
    // ADMIN luôn có quyền
    // User thường chỉ có quyền với data của chính mình
    // @param ownerId id của chủ sở hữu data
    public static void validateOwnership(Long ownerId) {
        if (isAdmin()) return; // Admin có thể làm mọi thứ
        Long currentUserId = getCurrentUserId();
        if (!currentUserId.equals(ownerId)) {
            throw new AccessDeniedException(
                    "Bạn không có quyền thao tác dữ liệu này");
        }
    }

    // Kiểm tra ownership mở rộng cho RECEPTIONIST
    // ADMIN + RECEPTIONIST đều có quyền
    // User thường chỉ có quyền với data của chính mình
    public static void validateOwnershipOrReceptionist(Long ownerId) {
        if (isAdminOrReceptionist()) return;
        Long currentUserId = getCurrentUserId();
        if (!currentUserId.equals(ownerId)) {
            throw new AccessDeniedException(
                    "Bạn không có quyền thao tác dữ liệu này");
        }
    }
}
