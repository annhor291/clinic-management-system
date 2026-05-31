package com.example.clinic.repository;

import com.example.clinic.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    // Tìm bác sĩ theo user_id
    Optional<Doctor> findByUserId(Long userId);

    // Kiểm tra bác sĩ đã tồn tại theo user_id chưa
    boolean existsByUserId(Long userId);

    // Lấy danh sách bác sĩ đang active theo chuyên khoa
    List<Doctor> findBySpecialtyIdAndActiveTrue(Long specialtyId);

    // Tìm kiếm bác sĩ theo tên, chuyên khoa, trạng thái — có pagination
    // JOIN FETCH để tránh N+1 query khi load specialty
    @Query("""
            SELECT d FROM Doctor d
            JOIN FETCH d.specialty s
            WHERE (:keyword IS NULL
                OR LOWER(d.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            AND (:specialtyId IS NULL OR s.id = :specialtyId)
            AND (:active IS NULL OR d.active = :active)
            """)
    Page<Doctor> searchDoctors(
            @Param("keyword") String keyword,
            @Param("specialtyId") Long specialtyId,
            @Param("active") Boolean active,
            Pageable pageable
    );

    // Kiểm tra chuyên khoa có đang được sử dụng không (trước khi xoá chuyên khoa)
    boolean existsBySpecialtyId(Long specialtyId);

    // Đếm số bác sĩ thuộc chuyên khoa (dùng cho SpecialtyResponse.totalDoctors)
    long countBySpecialtyId(Long specialtyId);
}
