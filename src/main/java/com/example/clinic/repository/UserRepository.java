package com.example.clinic.repository;

import com.example.clinic.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Tìm user theo email (dùng cho Spring Security load UserDetails)
    Optional<User> findByEmail(String email);

    // Tìm user theo username
    Optional<User> findByUsername(String username);

    // Kiểm tra email đã tồn tại chưa (dùng khi đăng ký)
    boolean existsByEmail(String email);

    // Kiểm tra username đã tồn tại chưa (dùng khi đăng ký)
    boolean existsByUsername(String username);

    // Tìm user theo reset token
    Optional<User> findByResetToken(String resetToken);
}
