package com.example.clinic.repository;

import com.example.clinic.entity.DoctorLeaveRequest;
import com.example.clinic.entity.enums.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

// Repository này sẽ được mở rộng đầy đủ ở Bước 4 (luồng đơn nghỉ).
// Hiện tại chỉ cần method để validate cảnh báo mềm khi đăng ký lịch tuần.
@Repository
public interface DoctorLeaveRequestRepository extends JpaRepository<DoctorLeaveRequest, Long> {

    Page<DoctorLeaveRequest> findByDoctorIdOrderByStartDateDesc(Long doctorId, Pageable pageable);

    Page<DoctorLeaveRequest> findByStatusOrderBySubmittedAtAsc(LeaveStatus status, Pageable pageable);

    // Tìm các đơn nghỉ ĐÃ DUYỆT của bác sĩ có khoảng thời gian giao với [rangeStart, rangeEnd]
    @Query("""
            SELECT l FROM DoctorLeaveRequest l
            WHERE l.doctor.id = :doctorId
            AND l.status = 'APPROVED'
            AND l.startDate <= :rangeEnd
            AND l.endDate >= :rangeStart
            """)
    List<DoctorLeaveRequest> findApprovedOverlapping(
            @Param("doctorId") Long doctorId,
            @Param("rangeStart") LocalDate rangeStart,
            @Param("rangeEnd") LocalDate rangeEnd
    );

    // Kiểm tra bác sĩ đã có đơn nghỉ khác (PENDING hoặc APPROVED) chồng lấn ngày chưa —
    // chặn cứng vì đây là lỗi dữ liệu, khác với warning mềm khi trùng lịch làm việc
    @Query("""
        SELECT COUNT(l) > 0 FROM DoctorLeaveRequest l
        WHERE l.doctor.id = :doctorId
        AND l.status IN ('PENDING', 'APPROVED')
        AND l.startDate <= :endDate
        AND l.endDate >= :startDate
        """)
    boolean existsOverlappingActiveRequest(
            @Param("doctorId") Long doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );


}
