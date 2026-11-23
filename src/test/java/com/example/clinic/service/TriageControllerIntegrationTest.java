package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.Patient;
import com.example.clinic.entity.Specialty;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.SpecialtyRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.JwtUtil;
import com.example.clinic.triage.TriageProperties;
import com.example.clinic.triage.TriageRuleBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Test qua HTTP thật (MockMvc + chuỗi bảo mật + JwtAuthFilter + GlobalExceptionHandler):
// endpoint công khai, token lạ, quyền dùng hồ sơ, mã lỗi, và không lộ thông tin riêng của bác sĩ.
public class TriageControllerIntegrationTest extends BaseIntegrationTest {

    private static final String URL = "/api/v1/triage/next";
    private static final String EMPTY = "{\"answers\":{}}";
    private static final String SORE_THROAT = "{\"answers\":{"
            + "\"AGE\":[\"ADULT\"],\"Q_RED_FLAGS\":[\"RF_NONE\"],\"Q_COMPLAINT\":[\"ENT\"],"
            + "\"Q_ENT_TYPE\":[\"SORE_THROAT\"],\"Q_ONSET\":[\"DAYS_1_3\"],\"Q_IMPACT\":[\"MILD\"],"
            + "\"Q_CARE_GROUP\":[\"CARE_NONE\"],\"Q_EXTRA\":[\"EXTRA_NONE\"]}}";

    @Autowired private WebApplicationContext context;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private TriageProperties properties;
    @Autowired
    private TriageRuleBook ruleBook;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private DoctorRepository doctorRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ===== Hỗ trợ =====

    private ResultActions postNext(String body, String token) throws Exception {
        MockHttpServletRequestBuilder request = post(URL).contentType(MediaType.APPLICATION_JSON).content(body);
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private User newUser(String prefix, Role role) {
        String id = prefix + "_" + UUID.randomUUID().toString().substring(0, 6);
        return userRepository.save(User.builder().username(id).email(id + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private Patient patientOf(User user) {
        return patientRepository.save(Patient.builder().user(user).fullName("Bệnh nhân thử")
                .dateOfBirth(LocalDate.now().minusYears(30)).build());
    }

    private static String withPatient(Long patientId) {
        return "{\"patientId\":" + patientId + ",\"answers\":{}}";
    }

    // ===== Công khai =====

    @Test
    void noToken_canStartTheQuestionnaire() throws Exception {
        postNext(EMPTY, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("QUESTION"))
                .andExpect(jsonPath("$.data.question.code").value("AGE"))
                .andExpect(jsonPath("$.data.emergencyNumber").value(properties.getEmergencyNumber()));
    }

    @Test
    void garbageToken_isIgnored_andTheEndpointStillWorks() throws Exception {
        postNext(EMPTY, "khong.phai.token").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("QUESTION"));
    }

    @Test
    void redFlag_returnsEmergencyOverHttp() throws Exception {
        postNext("{\"answers\":{\"AGE\":[\"ADULT\"],\"Q_RED_FLAGS\":[\"RF_CHEST\"]}}", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EMERGENCY"))
                .andExpect(jsonPath("$.data.message").isNotEmpty());
    }

    // ===== Dữ liệu sai → mã lỗi đúng =====

    @Test
    void missingAnswers_isBadRequest() throws Exception {
        postNext("{}", null).andExpect(status().isBadRequest());
    }

    @Test
    void invalidAnswer_isBadRequest() throws Exception {
        postNext("{\"answers\":{\"AGE\":[\"KHONG_CO\"]}}", null).andExpect(status().isBadRequest());
    }

    // ===== Hồ sơ bệnh nhân =====

    @Test
    void validToken_ownProfile_ageFromProfile() throws Exception {
        User user = newUser("bn", Role.PATIENT);
        Patient patient = patientOf(user);

        postNext(withPatient(patient.getId()), jwtUtil.generateToken(user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ageFromProfile").value(true))
                .andExpect(jsonPath("$.data.question.code").value("Q_RED_FLAGS"));
    }

    @Test
    void validToken_someoneElsesProfile_isForbidden() throws Exception {
        Patient theirs = patientOf(newUser("khac", Role.PATIENT));
        User me = newUser("toi", Role.PATIENT);

        postNext(withPatient(theirs.getId()), jwtUtil.generateToken(me)).andExpect(status().isForbidden());
    }

    @Test
    void noToken_withPatientId_isForbidden() throws Exception {
        Patient patient = patientOf(newUser("bn", Role.PATIENT));

        postNext(withPatient(patient.getId()), null).andExpect(status().isForbidden());
    }

    // ===== Không lộ thông tin riêng =====

    @Test
    void pass_exposesOnlyPublicDoctorInformation() throws Exception {
        Specialty ent = specialtyRepository.save(
                Specialty.builder().name(ruleBook.rules().specialties().get("ENT")).active(true).build());
        doctorRepository.save(Doctor.builder().user(newUser("bs", Role.DOCTOR)).specialty(ent)
                .fullName("Bác sĩ Công khai").phone("0901234567").active(true).build());

        postNext(SORE_THROAT, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PASS"))
                .andExpect(jsonPath("$.data.specialty.code").value("ENT"))
                .andExpect(jsonPath("$.data.disclaimer").isNotEmpty())
                .andExpect(jsonPath("$.data.doctors[0].fullName").value("Bác sĩ Công khai"))
                .andExpect(jsonPath("$.data.doctors[0].phone").doesNotExist())
                .andExpect(jsonPath("$.data.doctors[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.doctors[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.doctors[0].username").doesNotExist());
    }

    // ===== Việc mở công khai không lan sang chỗ khác =====

    @Test
    void otherEndpointsStayProtected() throws Exception {
        mockMvc.perform(get("/api/v1/doctors")).andExpect(status().is4xxClientError());
        mockMvc.perform(get(URL)).andExpect(status().is4xxClientError());
    }
}
