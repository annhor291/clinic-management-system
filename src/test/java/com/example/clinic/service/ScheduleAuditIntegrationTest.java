package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.Specialty;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuditActionType;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.DoctorScheduleRepository;
import com.example.clinic.repository.SpecialtyRepository;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

// Thao tác thủ công của admin trên ca làm việc phải có nhật ký; luồng nội bộ thì không ghi vào đây
public class ScheduleAuditIntegrationTest extends BaseIntegrationTest {

    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;

    @Test
    void manualDeactivateAndActivate_shouldBeAudited() {
        Specialty specialty = specialtyRepository.save(Specialty.builder().name("Audit test").active(true).build());
        User doctorUser = userRepository.save(User.builder().username("doctor_audit")
                .email("doctor_audit@example.com").password("x").role(Role.DOCTOR)
                .provider(AuthProvider.LOCAL).enabled(true).build());
        User admin = userRepository.save(User.builder().username("admin_audit")
                .email("admin_audit@example.com").password("x").role(Role.ADMIN)
                .provider(AuthProvider.LOCAL).enabled(true).build());
        Doctor doctor = doctorRepository.save(Doctor.builder().user(doctorUser).specialty(specialty)
                .fullName("BS Audit").consultationFee(new BigDecimal("200000")).active(true).build());

        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), LocalDate.now().plusDays(1), LocalTime.of(8, 0), LocalTime.of(10, 0),
                ShiftType.MORNING);
        Long scheduleId = scheduleRepository.findAll().get(0).getId();

        TestSecurityHelper.loginAs(admin);
        // Luồng nội bộ chưa được ghi vào nhật ký hệ thống
        assertThat(auditLogService.search(AuditActionType.SCHEDULE_MANUALLY_EDITED, "DOCTOR_SCHEDULE", 0, 10)
                .getTotalElements()).isZero();

        doctorScheduleService.deactivate(scheduleId);
        assertThat(auditLogService.search(AuditActionType.SCHEDULE_MANUALLY_CANCELLED, "DOCTOR_SCHEDULE", 0, 10)
                .getTotalElements()).isEqualTo(1);

        doctorScheduleService.activate(scheduleId);
        assertThat(auditLogService.search(AuditActionType.SCHEDULE_MANUALLY_EDITED, "DOCTOR_SCHEDULE", 0, 10)
                .getTotalElements()).isEqualTo(1);
    }
}
