package com.example.clinic.entity;

import com.example.clinic.entity.enums.ShiftType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.DayOfWeek;

@Entity
@Table(
        name = "doctor_weekly_registration_days",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_registration_day", columnNames = {"registration_id", "day_of_week"})
        }
)
@Data
@ToString(exclude = "registration")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DoctorWeeklyRegistrationDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private DoctorWeeklyRegistration registration;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private DayOfWeek dayOfWeek;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "shift_type", nullable = false, length = 10)
    private ShiftType shiftType;
}
