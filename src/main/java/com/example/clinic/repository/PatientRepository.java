package com.example.clinic.repository;

import com.example.clinic.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PatientRepository extends JpaRepository<Patient, Long> {
    // Tìm bệnh nhân theo user_id
    Optional<Patient> findByUserId(Long userId);

    // Kiểm tra bệnh nhân đã tồn tại theo user_id chưa
    boolean existsByUserId(Long userId);

    // Kiểm tra số BHYT đã tồn tại chưa (tránh trùng)
    boolean existsByInsuranceNumber(String insuranceNumber);

    // Kiểm tra số BHYT đã tồn tại chưa nhưng bỏ qua bệnh nhân hiện tại (dùng khi update)
    boolean existsByInsuranceNumberAndIdNot(String insuranceNumber, Long id);

    // Tìm kiếm bệnh nhân theo tên hoặc số điện thoại, có pagination
    // LOWER() để tìm không phân biệt hoa thường
    @Query("""
            SELECT p FROM Patient p
            WHERE (:keyword IS NULL
                OR LOWER(p.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR p.phone LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Patient> searchPatients(@Param("keyword") String keyword, Pageable pageable);
}
