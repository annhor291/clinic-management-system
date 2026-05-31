package com.example.clinic.repository;

import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.projection.AppointmentByDoctorCount;
import com.example.clinic.projection.AppointmentBySpecialtyCount;
import com.example.clinic.projection.AppointmentStatusCount;
import com.example.clinic.projection.MonthlyRevenueStats;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Tìm lịch hẹn theo booking code
    Optional<Appointment> findByBookingCode(String bookingCode);

    // Lấy danh sách lịch hẹn của bệnh nhân có pagination
    Page<Appointment> findByPatientIdOrderByAppointmentTimeDesc(Long patientId, Pageable pageable);

    // Lấy danh sách lịch hẹn của bác sĩ có pagination
    Page<Appointment> findByDoctorIdOrderByAppointmentTimeDesc(Long doctorId, Pageable pageable);

    // Lấy danh sách lịch hẹn của bệnh nhân theo status
    List<Appointment> findByPatientIdAndStatusOrderByAppointmentTimeDesc(
            Long patientId, AppointmentStatus status);

    // Kiểm tra slot đã được đặt chưa
    boolean existsByTimeSlotId(Long timeSlotId);

    // Đếm tổng số lịch hẹn trong khoảng thời gian
    long countByAppointmentTimeBetween(LocalDateTime from, LocalDateTime to);

    boolean existsByTimeSlotIdAndStatusNot(Long timeSlotId, AppointmentStatus status);

    // Tìm kiếm lịch hẹn có pagination + filter — dùng cho Admin dashboard
    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.patient p
            JOIN FETCH a.doctor d
            WHERE (:patientId IS NULL OR p.id = :patientId)
            AND (:doctorId IS NULL OR d.id = :doctorId)
            AND (:status IS NULL OR a.status = :status)
            AND (:fromDate IS NULL OR a.appointmentTime >= :fromDate)
            AND (:toDate IS NULL OR a.appointmentTime <= :toDate)
            ORDER BY a.appointmentTime DESC
            """)
    Page<Appointment> searchAppointments(
            @Param("patientId") Long patientId,
            @Param("doctorId") Long doctorId,
            @Param("status") AppointmentStatus status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    // Lấy lịch hẹn sắp tới chưa gửi nhắc 24h
    // Scheduler dùng query này để tìm và gửi email nhắc lịch
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status = 'CONFIRMED'
            AND a.reminder24hSent = false
            AND a.appointmentTime BETWEEN :from AND :to
            """)
    List<Appointment> findAppointmentsForReminder24h(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // Lấy lịch hẹn sắp tới chưa gửi nhắc 1h
    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status = 'CONFIRMED'
            AND a.reminder1hSent = false
            AND a.appointmentTime BETWEEN :from AND :to
            """)
    List<Appointment> findAppointmentsForReminder1h(
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );

    // QUERY THỐNG KÊ ADMIN DASHBOARD — dùng Projection thay vì Object[]

    // QUERY THỐNG KÊ ADMIN DASHBOARD
    // Dùng nativeQuery = true vì JPQL không hỗ trợ Projection
    // với các hàm tổng hợp COUNT, SUM, MONTH, YEAR
    // ================================================================

    // Thống kê số lịch hẹn theo trạng thái
    @Query(value = """
            SELECT a.status AS status, COUNT(a.id) AS count
            FROM appointments a
            WHERE a.appointment_time BETWEEN :fromDate AND :toDate
            GROUP BY a.status
            """, nativeQuery = true)
    List<AppointmentStatusCount> countByStatusAndDateRange(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    // Thống kê số lịch hẹn theo bác sĩ — top bác sĩ có nhiều lịch nhất
    @Query(value = """
            SELECT d.id AS doctorId,
                   d.full_name AS doctorName,
                   s.name AS specialtyName,
                   COUNT(a.id) AS totalAppointments
            FROM appointments a
            JOIN doctors d ON a.doctor_id = d.id
            JOIN specialties s ON d.specialty_id = s.id
            WHERE a.appointment_time BETWEEN :fromDate AND :toDate
            GROUP BY d.id, d.full_name, s.name
            ORDER BY COUNT(a.id) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<AppointmentByDoctorCount> countByDoctorAndDateRange(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("limit") int limit
    );

    // Thống kê số lịch hẹn theo chuyên khoa
    @Query(value = """
            SELECT s.id AS specialtyId,
                   s.name AS specialtyName,
                   COUNT(a.id) AS totalAppointments
            FROM appointments a
            JOIN doctors d ON a.doctor_id = d.id
            JOIN specialties s ON d.specialty_id = s.id
            WHERE a.appointment_time BETWEEN :fromDate AND :toDate
            GROUP BY s.id, s.name
            ORDER BY COUNT(a.id) DESC
            """, nativeQuery = true)
    List<AppointmentBySpecialtyCount> countBySpecialtyAndDateRange(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );

    // Thống kê doanh thu theo tháng
    @Query(value = """
            SELECT MONTH(a.appointment_time) AS month,
                   YEAR(a.appointment_time) AS year,
                   COUNT(a.id) AS totalAppointments,
                   SUM(d.consultation_fee) AS totalRevenue
            FROM appointments a
            JOIN doctors d ON a.doctor_id = d.id
            WHERE a.status = 'COMPLETED'
            AND a.appointment_time BETWEEN :fromDate AND :toDate
            GROUP BY YEAR(a.appointment_time), MONTH(a.appointment_time)
            ORDER BY YEAR(a.appointment_time) ASC, MONTH(a.appointment_time) ASC
            """, nativeQuery = true)
    List<MonthlyRevenueStats> getMonthlyRevenueStats(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate
    );
}
