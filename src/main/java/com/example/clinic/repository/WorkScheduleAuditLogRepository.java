package com.example.clinic.repository;

import com.example.clinic.entity.WorkScheduleAuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkScheduleAuditLogRepository extends JpaRepository<WorkScheduleAuditLog, Long> {

    Page<WorkScheduleAuditLog> findByDoctorIdOrderByPerformedAtDesc(Long doctorId, Pageable pageable);

}
