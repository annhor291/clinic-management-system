package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.UserRoleUpdateRequest;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuditActionType;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.AuditLogRepository;
import com.example.clinic.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

public class AuditLogIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private AuditLogRepository auditLogRepository;

    private User adminUser;
    private User targetUser;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(User.builder()
                .username("admin_audit_test").email("admin_audit_test@example.com")
                .password("x").role(Role.ADMIN).provider(AuthProvider.LOCAL).enabled(true).build());

        targetUser = userRepository.save(User.builder()
                .username("target_audit_test").email("target_audit_test@example.com")
                .password("x").role(Role.RECEPTIONIST).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    @Test
    void updateRole_shouldCreateAuditLogWithOldAndNewValue() {
        TestSecurityHelper.loginAs(adminUser);

        userService.updateRole(targetUser.getId(),
                UserRoleUpdateRequest.builder().role(Role.DOCTOR).build());

        var logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "USER", targetUser.getId(), PageRequest.of(0, 5));

        assertThat(logs.getContent()).isNotEmpty();
        var latest = logs.getContent().get(0);
        assertThat(latest.getAction()).isEqualTo(AuditActionType.USER_ROLE_CHANGED);
        assertThat(latest.getOldValue()).contains("RECEPTIONIST");
        assertThat(latest.getNewValue()).contains("DOCTOR");
    }

    @Test
    void lock_shouldCreateAuditLogWithoutSnapshot() {
        TestSecurityHelper.loginAs(adminUser);

        userService.lock(targetUser.getId());

        var logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                "USER", targetUser.getId(), PageRequest.of(0, 5));

        assertThat(logs.getContent()).anyMatch(l -> l.getAction() == AuditActionType.USER_LOCKED);
    }

    @Test
    void nonAdmin_cannotViewAuditLog() {
        TestSecurityHelper.loginAs(targetUser); // RECEPTIONIST, không phải ADMIN

        assertThatThrownBy(() -> auditLogService().getByTarget("USER", adminUser.getId(), 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Autowired
    private AuditLogService auditLogServiceField;

    private AuditLogService auditLogService() {
        return auditLogServiceField;
    }

}
