package com.example.clinic.repository;

import com.example.clinic.entity.Specialty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SpecialtyRepository extends JpaRepository<Specialty, Long> {

    // Tìm chuyên khoa theo tên
    Optional<Specialty> findByName(String name);

    // Kiểm tra tên chuyên khoa đã tồn tại chưa (tránh trùng khi tạo mới)
    boolean existsByName(String name);

    // Kiểm tra tên chuyên khoa đã tồn tại chưa nhưng bỏ qua chuyên khoa hiện tại (dùng khi update)
    boolean existsByNameAndIdNot(String name, Long id);

    // Lấy danh sách chuyên khoa đang active
    List<Specialty> findByActiveTrue();

    // Tìm kiếm chuyên khoa theo tên, có pagination
    @Query("""
            SELECT s FROM Specialty s
            WHERE (:keyword IS NULL
                OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:active IS NULL OR s.active = :active)
            """)
    Page<Specialty> searchSpecialties(
            @Param("keyword") String keyword,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
