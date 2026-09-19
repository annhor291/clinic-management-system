package com.example.clinic.repository;

import com.example.clinic.entity.DoctorWeeklyRegistration;
import com.example.clinic.entity.enums.RegistrationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DoctorWeeklyRegistrationRepository extends JpaRepository<DoctorWeeklyRegistration, Long> {

    boolean existsByDoctorIdAndWeekStartDate(Long doctorId, LocalDate weekStartDate);

    // JOIN FETCH days để tránh lazy-loading lỗi ngoài transaction khi map sang response
    @Query("SELECT r FROM DoctorWeeklyRegistration r JOIN FETCH r.days WHERE r.id = :id")
    Optional<DoctorWeeklyRegistration> findByIdWithDays(@Param("id") Long id);

    Page<DoctorWeeklyRegistration> findByDoctorIdOrderByWeekStartDateDesc(Long doctorId, Pageable pageable);

    Page<DoctorWeeklyRegistration> findByStatusOrderBySubmittedAtAsc(RegistrationStatus status, Pageable pageable);

}
