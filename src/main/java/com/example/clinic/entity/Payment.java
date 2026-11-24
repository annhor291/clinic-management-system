    package com.example.clinic.entity;

    import com.example.clinic.entity.enums.PaymentStatus;
    import jakarta.persistence.*;
    import lombok.*;
    import org.hibernate.annotations.JdbcTypeCode;
    import org.hibernate.type.SqlTypes;

    import java.math.BigDecimal;
    import java.time.LocalDateTime;

    @Entity
    @Table(name = "payments")
    @Data
    @ToString(exclude = {"appointment", "paymentMethod"})
    @EqualsAndHashCode(onlyExplicitlyIncluded = true)
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class Payment {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @EqualsAndHashCode.Include
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "appointment_id", nullable = false)
        private Appointment appointment;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "payment_method_id", nullable = false)
        private PaymentMethod paymentMethod;

        // Mã giao dịch phía hệ thống mình (gửi sang cổng làm vnp_TxnRef)
        @Column(name = "transaction_code", nullable = false, unique = true, length = 100)
        private String transactionCode;

        // Mã giao dịch do cổng trả về (vnp_TransactionNo) — cần dùng lại khi hoàn tiền
        @Column(name = "gateway_code", length = 100)
        private String gatewayCode;

        // Đúng giá trị vnp_CreateDate đã gửi VNPay; hoàn tiền phải gửi lại nguyên giá trị này (vnp_TransactionDate)
        @Column(name = "gateway_create_date", length = 14)
        private String gatewayCreateDate;

        @Column(name = "refund_request_id", length = 32)
        private String refundRequestId;

        // Phản hồi thô của VNPay khi hoàn tiền, để đối soát
        @Column(name = "refund_response", columnDefinition = "TEXT")
        private String refundResponse;

        @Column(name = "amount", nullable = false, precision = 12, scale = 0)
        private BigDecimal amount;

        @Enumerated(EnumType.STRING)
        @JdbcTypeCode(SqlTypes.VARCHAR)
        @Column(name = "status", nullable = false, length = 20)
        @Builder.Default
        private PaymentStatus status = PaymentStatus.PENDING;

        @Column(name = "refund_amount", precision = 12, scale = 0)
        @Builder.Default
        private BigDecimal refundAmount = BigDecimal.ZERO;

        @Column(name = "refunded_at")
        private LocalDateTime refundedAt;

        @Column(name = "refund_reason", columnDefinition = "TEXT")
        private String refundReason;

        // Raw response từ cổng (JSON/query string) — lưu để đối soát và debug
        @Column(name = "gateway_response", columnDefinition = "TEXT")
        private String gatewayResponse;

        @Column(name = "payment_url", length = 1000)
        private String paymentUrl;

        @Column(name = "paid_at")
        private LocalDateTime paidAt;

        @Column(name = "expired_at")
        private LocalDateTime expiredAt;

        @Version
        @Column(name = "version")
        private Long version;

        @Column(name = "created_at", updatable = false)
        private LocalDateTime createdAt;

        @Column(name = "updated_at")
        private LocalDateTime updatedAt;

        @PrePersist
        protected void onCreate() {
            createdAt = LocalDateTime.now();
            updatedAt = LocalDateTime.now();
        }

        @PreUpdate
        protected void onUpdate() {
            updatedAt = LocalDateTime.now();
        }

    }
