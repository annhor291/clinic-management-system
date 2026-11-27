package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Quyền truy cập 4 endpoint danh sách dành cho nhân viên/admin, kiểm tra qua HTTP thật
// (MockMvc + chuỗi bảo mật), để chắc rằng rule trong SecurityConfig khớp cả đường dẫn gốc.
public class AdminListEndpointsIntegrationTest extends BaseIntegrationTest {

    private static final String PAYMENTS = "/api/v1/payments";
    private static final String REGISTRATIONS = "/api/v1/work-schedule/registrations";
    private static final String LEAVES = "/api/v1/work-schedule/leave-requests";
    private static final String WORK_AUDIT = "/api/v1/work-schedule/audit-logs";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtil jwtUtil;
    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;
    private String adminToken;
    private String receptionistToken;
    private String doctorToken;
    private String patientToken;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        adminToken = tokenOf(Role.ADMIN);
        receptionistToken = tokenOf(Role.RECEPTIONIST);
        doctorToken = tokenOf(Role.DOCTOR);
        patientToken = tokenOf(Role.PATIENT);
    }

    private String tokenOf(Role role) {
        String id = role.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 6);
        User user = userRepository.save(User.builder().username(id).email(id + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
        return jwtUtil.generateToken(user);
    }

    private ResultActions call(String url, String token) throws Exception {
        MockHttpServletRequestBuilder request = get(url);
        if (token != null) request.header("Authorization", "Bearer " + token);
        return mockMvc.perform(request);
    }

    // ===== GET /payments: chỉ ADMIN =====

    @Test
    void payments_admin_ok_andPageShape() throws Exception {
        call(PAYMENTS, adminToken).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void payments_notAdmin_orNoToken_isRejected() throws Exception {
        call(PAYMENTS, receptionistToken).andExpect(status().isForbidden());
        call(PAYMENTS, doctorToken).andExpect(status().isForbidden());
        call(PAYMENTS, patientToken).andExpect(status().isForbidden());
        call(PAYMENTS, null).andExpect(status().is4xxClientError());
    }

    @Test
    void payments_invalidStatus_isBadRequest() throws Exception {
        call(PAYMENTS + "?status=KHONG_CO", adminToken).andExpect(status().isBadRequest());
    }

    // ===== 3 endpoint của lịch làm việc: ADMIN + RECEPTIONIST =====

    @Test
    void workScheduleLists_adminAndReceptionist_ok() throws Exception {
        for (String url : new String[]{REGISTRATIONS, LEAVES, WORK_AUDIT}) {
            call(url, adminToken).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
            call(url, receptionistToken).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());
        }
    }

    @Test
    void workScheduleLists_doctorAndPatient_forbidden() throws Exception {
        for (String url : new String[]{REGISTRATIONS, LEAVES, WORK_AUDIT}) {
            call(url, doctorToken).andExpect(status().isForbidden());
            call(url, patientToken).andExpect(status().isForbidden());
            call(url, null).andExpect(status().is4xxClientError());
        }
    }

    @Test
    void registrationsAndLeaves_statusFilter_acceptsValidAndRejectsInvalid() throws Exception {
        call(REGISTRATIONS + "?status=PENDING", receptionistToken).andExpect(status().isOk());
        call(LEAVES + "?status=APPROVED", receptionistToken).andExpect(status().isOk());
        call(REGISTRATIONS + "?status=KHONG_CO", receptionistToken).andExpect(status().isBadRequest());
        call(LEAVES + "?status=KHONG_CO", receptionistToken).andExpect(status().isBadRequest());
    }
}
