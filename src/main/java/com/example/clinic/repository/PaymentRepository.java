package com.example.clinic.repository;

import com.example.clinic.entity.Payment;
import com.example.clinic.entity.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionCode(String transactionCode);

    // Khoá dòng khi xử lý IPN/return — tránh 2 callback trùng xử lý cùng lúc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.transactionCode = :code")
    Optional<Payment> findByTransactionCodeForUpdate(@Param("code") String code);

    List<Payment> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);

    boolean existsByAppointmentIdAndStatusIn(Long appointmentId, Collection<PaymentStatus> statuses);

    Optional<Payment> findFirstByAppointmentIdAndStatus(Long appointmentId, PaymentStatus status);

    // Quy tắc A: appointment còn giao dịch PENDING chưa hết hạn thì chưa được expire
    boolean existsByAppointmentIdAndStatusAndExpiredAtAfter(
            Long appointmentId, PaymentStatus status, LocalDateTime now);

    // Scheduler dọn giao dịch PENDING đã quá hạn
    List<Payment> findByStatusAndExpiredAtBefore(PaymentStatus status, LocalDateTime now);

    // Giao dịch PENDING còn hạn của 1 lịch hẹn — dùng để trả lại giao dịch cũ thay vì tạo trùng
    Optional<Payment> findFirstByAppointmentIdAndStatusAndExpiredAtAfter(
            Long appointmentId, PaymentStatus status, LocalDateTime now);

    @Query("select p.appointment.id from Payment p where p.transactionCode = :transactionCode")
    Optional<Long> findAppointmentIdByTransactionCode(@Param("transactionCode") String transactionCode);

    @Query("select p.id from Payment p where p.status = :status and p.expiredAt < :now")
    List<Long> findOverdueIds(@Param("status") PaymentStatus status, @Param("now") LocalDateTime now);

    @Query("select p.appointment.id from Payment p where p.id = :id")
    Optional<Long> findAppointmentIdByPaymentId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    Optional<Payment> findFirstByAppointmentIdOrderByCreatedAtDescIdDesc(Long appointmentId);

    // Danh sách cho admin: JOIN FETCH các quan hệ *-to-one để map response không bị lazy-loading,
    // countQuery tách riêng vì không đếm kèm fetch
    @Query(value = """
            select p from Payment p
            join fetch p.appointment a
            join fetch a.patient
            join fetch p.paymentMethod
            order by p.createdAt desc, p.id desc
            """,
            countQuery = "select count(p) from Payment p")
    Page<Payment> findAllForAdmin(Pageable pageable);

    @Query(value = """
            select p from Payment p
            join fetch p.appointment a
            join fetch a.patient
            join fetch p.paymentMethod
            where p.status = :status
            order by p.createdAt desc, p.id desc
            """,
            countQuery = "select count(p) from Payment p where p.status = :status")
    Page<Payment> findAllForAdminByStatus(@Param("status") PaymentStatus status, Pageable pageable);
}
