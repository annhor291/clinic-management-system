package com.example.clinic.repository;


import com.example.clinic.entity.TimeSlot;
import com.example.clinic.entity.enums.SlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    // Lấy tất cả slot của bác sĩ trong 1 ngày
    List<TimeSlot> findByDoctorIdAndSlotDateOrderByStartTimeAsc(Long doctorId, LocalDate slotDate);

    // Lấy tất cả slot AVAILABLE của bác sĩ trong 1 ngày
    // Đây là query check lịch trống
    List<TimeSlot> findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
            Long doctorId, LocalDate slotDate, SlotStatus status);

    // Lấy slot theo schedule
    List<TimeSlot> findByScheduleId(Long scheduleId);

    // Kiểm tra slot đã tồn tại chưa (tránh tạo trùng khi sinh slot từ schedule)
    boolean existsByDoctorIdAndSlotDateAndStartTime(
            Long doctorId, LocalDate slotDate, java.time.LocalTime startTime);

    // Lấy slot theo bác sĩ trong khoảng ngày — dùng để xem lịch theo tuần
    @Query("""
            SELECT ts FROM TimeSlot ts
            WHERE ts.doctor.id = :doctorId
            AND ts.slotDate BETWEEN :startDate AND :endDate
            ORDER BY ts.slotDate ASC, ts.startTime ASC
            """)
    List<TimeSlot> findByDoctorIdAndDateRange(
            @Param("doctorId") Long doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Lấy slot AVAILABLE của bác sĩ trong khoảng ngày
    @Query("""
            SELECT ts FROM TimeSlot ts
            WHERE ts.doctor.id = :doctorId
            AND ts.slotDate BETWEEN :startDate AND :endDate
            AND ts.status = 'AVAILABLE'
            ORDER BY ts.slotDate ASC, ts.startTime ASC
            """)
    List<TimeSlot> findAvailableByDoctorIdAndDateRange(
            @Param("doctorId") Long doctorId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Tìm slot với pessimistic lock để chống double booking
    // PESSIMISTIC_WRITE: lock row lại khi đọc → transaction khác phải chờ transaction hiện tại hoàn thành
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ts FROM TimeSlot ts WHERE ts.id = :id")
    Optional<TimeSlot> findByIdWithLock(@Param("id") Long id);
}
