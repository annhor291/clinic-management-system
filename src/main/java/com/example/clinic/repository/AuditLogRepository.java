package com.example.clinic.repository;

import com.example.clinic.entity.AuditLog;
import com.example.clinic.entity.enums.AuditActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
            String entityType, Long entityId, Pageable pageable);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:action IS NULL OR a.action = :action)
            AND (:entityType IS NULL OR a.entityType = :entityType)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> search(
            @Param("action") AuditActionType action,
            @Param("entityType") String entityType,
            Pageable pageable
    );
}
