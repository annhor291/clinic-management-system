package com.example.clinic.repository;

import com.example.clinic.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, Long> {

    // Tìm ca làm việc theo bác sĩ và ngày cụ thể
    Optional<DoctorSchedule> findByDoctorIdAndWorkDate(Long doctorId, LocalDate workDate);

    // Kiểm tra bác sĩ đã có ca làm việc trong ngày đó chưa
    boolean existsByDoctorIdAndWorkDate(Long doctorId, LocalDate workDate);

    // Lấy tất cả ca làm việc của bác sĩ từ ngày bắt đầu đến ngày kết thúc
    // Dùng để xem lịch theo tuần
    @Query("""
            SELECT ds FROM DoctorSchedule ds
            WHERE ds.doctor.id = :doctorId
            AND ds.workDate BETWEEN :startDate AND :endDate
            AND ds.active = true
            ORDER BY ds.workDate ASC, ds.startTime ASC
            """)
    List<DoctorSchedule> findByDoctorIdAndDateRange(
            @Param("doctorId") Long doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Lấy tất cả ca làm việc active của bác sĩ từ hôm nay trở đi
    @Query("""
            SELECT ds FROM DoctorSchedule ds
            WHERE ds.doctor.id = :doctorId
            AND ds.workDate >= :fromDate
            AND ds.active = true
            ORDER BY ds.workDate ASC
            """)
    List<DoctorSchedule> findUpcomingByDoctorId(
            @Param("doctorId") Long doctorId,
            @Param("fromDate") LocalDate fromDate
    );
}
