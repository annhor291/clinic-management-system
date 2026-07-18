package com.example.clinic.service.impl;

import com.example.clinic.dto.request.*;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.UserResponse;
import com.example.clinic.dto.response.UserStatisticsResponse;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.mapper.UserMapper;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getAll(String keyword, Role role, Boolean enabled, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<User> userPage = userRepository.search(
                (keyword == null || keyword.isBlank()) ? null : keyword.trim(),
                role,
                enabled,
                pageable
        );

        return PageResponse.of(userPage.map(userMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return userMapper.toResponse(findByIdOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe() {
        return getById(SecurityUtil.getCurrentUserId());
    }

    @Override
    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email đã tồn tại");
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .locked(false)
                .build();

        User saved = userRepository.save(user);
        return userMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse update(Long id, UserUpdateRequest request) {
        User user = findActiveByIdOrThrow(id);

        if (!user.getUsername().equals(request.getUsername())
                && userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username đã tồn tại");
        }
        if (!user.getEmail().equals(request.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email đã tồn tại");
        }

        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setRole(request.getRole());

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse updateRole(Long id, UserRoleUpdateRequest request) {
        User user = findActiveByIdOrThrow(id);

        // Không cho admin tự hạ quyền chính mình khỏi ADMIN — tránh khoá quyền truy cập của chính mình
        if (id.equals(SecurityUtil.getCurrentUserId()) && request.getRole() != Role.ADMIN) {
            throw new IllegalStateException("Không thể tự đổi vai trò của chính mình sang vai trò khác ADMIN");
        }

        user.setRole(request.getRole());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse activate(Long id) {
        User user = findActiveByIdOrThrow(id);

        if (user.isEnabled()) {
            throw new IllegalStateException("Tài khoản này đang ở trạng thái hoạt động");
        }

        user.setEnabled(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse deactivate(Long id) {
        guardNotSelf(id, "Không thể tự vô hiệu hoá tài khoản của chính mình");

        User user = findActiveByIdOrThrow(id);

        if (!user.isEnabled()) {
            throw new IllegalStateException("Tài khoản này đang ở trạng thái vô hiệu hoá");
        }

        user.setEnabled(false);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse lock(Long id) {
        guardNotSelf(id, "Không thể tự khoá tài khoản của chính mình");

        User user = findActiveByIdOrThrow(id);

        if (user.isLocked()) {
            throw new IllegalStateException("Tài khoản này đã bị khoá");
        }

        user.setLocked(true);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse unlock(Long id) {
        User user = findActiveByIdOrThrow(id);

        if (!user.isLocked()) {
            throw new IllegalStateException("Tài khoản này không bị khoá");
        }

        user.setLocked(false);
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void resetPassword(Long id, AdminResetPasswordRequest request) {
        User user = findActiveByIdOrThrow(id);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        guardNotSelf(id, "Không thể tự xoá tài khoản của chính mình");

        User user = findActiveByIdOrThrow(id);
        user.setDeletedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserStatisticsResponse getStatistics() {
        Map<Role, Long> countByRole = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            countByRole.put(role, userRepository.countByRoleAndDeletedAtIsNull(role));
        }

        return UserStatisticsResponse.builder()
                .totalUsers(userRepository.countByDeletedAtIsNull())
                .totalActive(userRepository.countByEnabledTrueAndDeletedAtIsNull())
                .totalInactive(userRepository.countByEnabledFalseAndDeletedAtIsNull())
                .totalLocked(userRepository.countByLockedTrueAndDeletedAtIsNull())
                .countByRole(countByRole)
                .build();
    }

    @Override
    @Transactional
    public List<UserResponse> bulkActivate(BulkUserIdsRequest request) {
        List<User> users = findAllActiveByIdOrThrow(request.getIds());
        users.forEach(u -> u.setEnabled(true));
        return userMapper.toResponseList(userRepository.saveAll(users));
    }

    @Override
    @Transactional
    public List<UserResponse> bulkDeactivate(BulkUserIdsRequest request) {
        if (request.getIds().contains(SecurityUtil.getCurrentUserId())) {
            throw new IllegalStateException("Không thể tự vô hiệu hoá tài khoản của chính mình");
        }

        List<User> users = findAllActiveByIdOrThrow(request.getIds());
        users.forEach(u -> u.setEnabled(false));
        return userMapper.toResponseList(userRepository.saveAll(users));
    }

    @Override
    @Transactional
    public void bulkDelete(BulkUserIdsRequest request) {
        if (request.getIds().contains(SecurityUtil.getCurrentUserId())) {
            throw new IllegalStateException("Không thể tự xoá tài khoản của chính mình");
        }

        List<User> users = findAllActiveByIdOrThrow(request.getIds());
        LocalDateTime now = LocalDateTime.now();
        users.forEach(u -> u.setDeletedAt(now));
        userRepository.saveAll(users);
    }

    // ===== Private helper =====

    private User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy user với id: " + id));
    }

    // Dùng cho mọi thao tác thay đổi dữ liệu — chặn nếu user đã bị soft-delete
    private User findActiveByIdOrThrow(Long id) {
        User user = findByIdOrThrow(id);
        if (user.getDeletedAt() != null) {
            throw new IllegalStateException("Tài khoản này đã bị xoá, không thể thao tác");
        }
        return user;
    }

    private List<User> findAllByIdOrThrow(List<Long> ids) {
        List<User> users = userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            throw new ResourceNotFoundException("Một số user trong danh sách không tồn tại");
        }
        return users;
    }

    private List<User> findAllActiveByIdOrThrow(List<Long> ids) {
        List<User> users = userRepository.findAllById(ids);
        if (users.size() != ids.size()) {
            throw new ResourceNotFoundException("Một số user trong danh sách không tồn tại");
        }
        boolean hasDeleted = users.stream().anyMatch(u -> u.getDeletedAt() != null);
        if (hasDeleted) {
            throw new IllegalStateException("Danh sách chứa tài khoản đã bị xoá");
        }
        return users;
    }

    private void guardNotSelf(Long id, String message) {
        if (id.equals(SecurityUtil.getCurrentUserId())) {
            throw new IllegalStateException(message);
        }
    }
}
