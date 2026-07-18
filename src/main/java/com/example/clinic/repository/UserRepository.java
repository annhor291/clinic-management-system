package com.example.clinic.repository;

import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Chỉ tìm user chưa bị xoá mềm — dùng cho login
    @Query("SELECT u FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.deletedAt IS NULL")
    Optional<User> findByUsername(@Param("username") String username);

    // Email/username của user đã xoá mềm được coi như "trống" để có thể tạo lại
    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.email = :email AND u.deletedAt IS NULL")
    boolean existsByEmail(@Param("email") String email);

    @Query("SELECT COUNT(u) > 0 FROM User u WHERE u.username = :username AND u.deletedAt IS NULL")
    boolean existsByUsername(@Param("username") String username);

    // Tìm user theo reset token
    Optional<User> findByResetToken(String resetToken);

    // Danh sách user có pagination + filter, luôn loại bỏ user đã xoá mềm
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL " +
            "AND (:keyword IS NULL OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:role IS NULL OR u.role = :role) " +
            "AND (:enabled IS NULL OR u.enabled = :enabled)")
    Page<User> search(@Param("keyword") String keyword,
                      @Param("role") Role role,
                      @Param("enabled") Boolean enabled,
                      Pageable pageable);

    // ===== Dùng cho thống kê =====
    long countByDeletedAtIsNull();
    long countByEnabledTrueAndDeletedAtIsNull();
    long countByEnabledFalseAndDeletedAtIsNull();
    long countByLockedTrueAndDeletedAtIsNull();
    long countByRoleAndDeletedAtIsNull(Role role);
}
