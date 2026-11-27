package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.NotificationType;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.NotificationRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// POST /notifications/broadcast: chỉ ADMIN; chỉ gửi cho tài khoản hợp lệ; không gửi email
public class BroadcastNotificationIntegrationTest extends BaseIntegrationTest {

    private static final String URL = "/api/v1/notifications/broadcast";

    @Autowired
    private WebApplicationContext context;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;
    private User admin, patient1, patient2, doctor, lockedPatient, unverifiedPatient;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity()).build();
        admin = user(Role.ADMIN, true, true);
        patient1 = user(Role.PATIENT, true, true);
        patient2 = user(Role.PATIENT, true, true);
        doctor = user(Role.DOCTOR, true, true);
        lockedPatient = user(Role.PATIENT, true, true);
        lockedPatient.setLocked(true);
        userRepository.save(lockedPatient);
        unverifiedPatient = user(Role.PATIENT, true, false);
    }

    private User user(Role role, boolean enabled, boolean emailVerified) {
        String id = role.name().toLowerCase() + "_" + UUID.randomUUID().toString().substring(0, 6);
        return userRepository.save(User.builder().username(id).email(id + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL)
                .enabled(enabled).emailVerified(emailVerified).build());
    }

    private ResultActions send(User asUser, String json) throws Exception {
        var request = post(URL).contentType(MediaType.APPLICATION_JSON).content(json);
        if (asUser != null) request.header("Authorization", "Bearer " + jwtUtil.generateToken(asUser));
        return mockMvc.perform(request);
    }

    private long generalCount(User u) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(u.getId(), PageRequest.of(0, 10))
                .getContent().stream().filter(n -> n.getType() == NotificationType.GENERAL).count();
    }

    @Test
    void admin_broadcastToPatients_reachesOnlyValidPatients_andSendsNoEmail() throws Exception {
        send(admin, "{\"title\":\"Nghỉ lễ\",\"content\":\"Phòng khám nghỉ lễ 2/9\",\"targetRole\":\"PATIENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients").value(2));

        assertThat(generalCount(patient1)).isEqualTo(1);
        assertThat(generalCount(patient2)).isEqualTo(1);
        assertThat(generalCount(doctor)).isZero();
        assertThat(generalCount(lockedPatient)).isZero();
        assertThat(generalCount(unverifiedPatient)).isZero();
        verify(emailService, never()).sendEmailSync(any(), any(), any());
    }

    @Test
    void admin_broadcastToAll_reachesEveryValidAccount() throws Exception {
        send(admin, "{\"title\":\"Bảo trì\",\"content\":\"Hệ thống bảo trì tối nay\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recipients").value(4)); // admin, 2 bệnh nhân, 1 bác sĩ
    }

    @Test
    void nonAdmin_orNoToken_isRejected() throws Exception {
        String body = "{\"title\":\"T\",\"content\":\"C\"}";
        send(patient1, body).andExpect(status().isForbidden());
        send(doctor, body).andExpect(status().isForbidden());
        send(null, body).andExpect(status().is4xxClientError());
    }

    @Test
    void blankTitle_orContent_isBadRequest() throws Exception {
        send(admin, "{\"title\":\"\",\"content\":\"C\"}").andExpect(status().isBadRequest());
        send(admin, "{\"title\":\"T\",\"content\":\"   \"}").andExpect(status().isBadRequest());
    }
}
