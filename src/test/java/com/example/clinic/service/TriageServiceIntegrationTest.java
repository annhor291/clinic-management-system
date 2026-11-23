package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.config.ClinicProperties;
import com.example.clinic.dto.request.TriageRequest;
import com.example.clinic.dto.response.TriageResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.repository.*;
import com.example.clinic.triage.TriageMessages;
import com.example.clinic.triage.TriageProperties;
import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.engine.TriageFlowEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test tầng Service với DB thật: nhóm tuổi từ hồ sơ, quyền dùng hồ sơ, và ghép kết quả PASS với bác sĩ thật.
// Logic hỏi-đáp/chấm điểm đã có bộ test riêng ở package triage.engine nên ở đây chỉ kiểm tra phần "ghép nối".
public class TriageServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired private TriageService triageService;
    @Autowired private TriageRuleBook ruleBook;
    @Autowired private TriageProperties properties;
    @Autowired private ClinicProperties clinicProperties;
    @Autowired
    private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;

    @BeforeEach
    void noLoginByDefault() {
        SecurityContextHolder.clearContext();
    }

    // ===== Dữ liệu mẫu =====

    private static Map<String, List<String>> ans(String... kv) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], List.of(kv[i + 1].split(",")));
        }
        return m;
    }

    // Đủ mọi câu hỏi của luồng "đau họng nhẹ"; age = null thì không gửi câu về tuổi (để hồ sơ tự điền)
    private static Map<String, List<String>> sorethroat(String age) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        if (age != null) m.put("AGE", List.of(age));
        m.putAll(ans("Q_RED_FLAGS", "RF_NONE", "Q_COMPLAINT", "ENT", "Q_ENT_TYPE", "SORE_THROAT",
                "Q_ONSET", "DAYS_1_3", "Q_IMPACT", "MILD", "Q_CARE_GROUP", "CARE_NONE", "Q_EXTRA", "EXTRA_NONE"));
        return m;
    }

    // Đủ mọi câu hỏi của luồng "đau" với vị trí tuỳ chọn
    private static Map<String, List<String>> pain(String area) {
        return ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_NONE", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", area,
                "Q_QUALITY", "DULL", "Q_ONSET", "DAYS_1_3", "Q_PATTERN", "OCCASIONAL", "Q_IMPACT", "MILD",
                "Q_CARE_GROUP", "CARE_NONE", "Q_EXTRA", "EXTRA_NONE");
    }

    private TriageResponse ask(Long patientId, Map<String, List<String>> answers) {
        return triageService.next(new TriageRequest(patientId, answers));
    }

    private User newUser(String prefix, Role role) {
        String id = prefix + "_" + UUID.randomUUID().toString().substring(0, 6);
        return userRepository.save(User.builder().username(id).email(id + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private Specialty entSpecialty(boolean active) {
        return specialtyRepository.save(
                Specialty.builder().name(ruleBook.rules().specialties().get("ENT")).active(active).build());
    }

    private Doctor newDoctor(Specialty specialty, String fullName, boolean active) {
        return doctorRepository.save(Doctor.builder().user(newUser("bs", Role.DOCTOR))
                .specialty(specialty).fullName(fullName).active(active).build());
    }

    private void addSlot(Doctor doctor, LocalDate date, LocalTime start, SlotStatus status) {
        DoctorSchedule schedule = scheduleRepository.save(DoctorSchedule.builder()
                .doctor(doctor).workDate(date).startTime(start).endTime(start.plusHours(1))
                .slotDurationMinutes(30).shiftType(ShiftType.CUSTOM).active(true).build());
        timeSlotRepository.save(TimeSlot.builder().schedule(schedule).doctor(doctor).slotDate(date)
                .startTime(start).endTime(start.plusMinutes(30)).status(status).queueNumber(1).build());
    }

    private Patient patientOf(User user, LocalDate dateOfBirth) {
        return patientRepository.save(Patient.builder().user(user).fullName("Bệnh nhân thử")
                .dateOfBirth(dateOfBirth).build());
    }

    // ===== Người dùng chưa đăng nhập =====

    @Test
    void anonymous_firstStepAsksAge() {
        TriageResponse r = ask(null, ans());

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.QUESTION);
        assertThat(r.getQuestion().getCode()).isEqualTo("AGE");
        assertThat(r.getQuestion().getOptions()).hasSize(4);
        assertThat(r.isAgeFromProfile()).isFalse();
        assertThat(r.getEmergencyNumber()).isEqualTo(properties.getEmergencyNumber());
    }

    @Test
    void redFlag_isEmergency() {
        TriageResponse r = ask(null, ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_CHEST"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.EMERGENCY);
        assertThat(r.getMessage()).isEqualTo(TriageMessages.emergency(properties.getEmergencyNumber()));
        assertThat(r.getSupportMessage()).isNull();
        assertThat(r.getDoctors()).isNull();
    }

    @Test
    void selfHarmOnly_getsSupportiveMessage() {
        TriageResponse r = ask(null, ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_SELF_HARM"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.EMERGENCY);
        assertThat(r.getMessage()).isEqualTo(TriageMessages.selfHarm(properties.getEmergencyNumber()));
        assertThat(r.getSupportMessage()).isNull();
    }

    @Test
    void selfHarmWithAnotherFlag_getsBothMessages() {
        TriageResponse r = ask(null, ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_CHEST,RF_SELF_HARM"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.EMERGENCY);
        assertThat(r.getMessage()).isEqualTo(TriageMessages.emergency(properties.getEmergencyNumber()));
        assertThat(r.getSupportMessage()).isEqualTo(TriageMessages.selfHarm(properties.getEmergencyNumber()));
    }

    @Test
    void escalationRule_isReferredToStaff() {
        TriageResponse r = ask(null, pain("CHEST"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.REFER);
        assertThat(r.getReasonCodes()).contains("ESC_CHEST_PAIN");
        assertThat(r.getReasons()).isNotEmpty();
        assertThat(r.getMessage()).isEqualTo(
                TriageMessages.refer(clinicProperties.getPhone(), properties.getEmergencyNumber()));
    }

    @Test
    void notConfidentEnough_isReferredWithGateReason() {
        TriageResponse r = ask(null, pain("ABD_LOWER_OTHER"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.REFER);
        assertThat(r.getReasonCodes()).containsExactly("LOW_SCORE");
        assertThat(r.getReasons()).containsExactly(TriageMessages.LOW_CONFIDENCE_REASON);
    }

    @Test
    void invalidAnswers_areRejected() {
        assertThatThrownBy(() -> ask(null, ans("AGE", "KHONG_CO")))
                .isInstanceOf(IllegalStateException.class);
    }

    // ===== Kết quả PASS ghép với bác sĩ thật =====

    @Test
    void pass_returnsSpecialtyAndDoctors_earliestSlotFirst() {
        Specialty ent = entSpecialty(true);
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        Doctor later = newDoctor(ent, "Bác sĩ Chiều", true);
        addSlot(later, tomorrow, LocalTime.of(14, 0), SlotStatus.AVAILABLE);
        Doctor earlier = newDoctor(ent, "Bác sĩ Sáng", true);
        addSlot(earlier, tomorrow, LocalTime.of(8, 0), SlotStatus.AVAILABLE);
        // Chỉ có slot đã đặt → không có lịch trống; tên đứng đầu bảng chữ cái nhưng phải xếp cuối
        Doctor noFreeSlot = newDoctor(ent, "An", true);
        addSlot(noFreeSlot, tomorrow, LocalTime.of(9, 0), SlotStatus.BOOKED);
        // Bác sĩ đã ngừng làm việc và bác sĩ chuyên khoa khác không được xuất hiện
        Doctor inactive = newDoctor(ent, "Bác sĩ Nghỉ", false);
        addSlot(inactive, tomorrow, LocalTime.of(7, 0), SlotStatus.AVAILABLE);
        Specialty other = specialtyRepository.save(Specialty.builder().name("Chuyên khoa khác").active(true).build());
        newDoctor(other, "Bác sĩ Khác", true);

        TriageResponse r = ask(null, sorethroat("ADULT"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.PASS);
        assertThat(r.getSpecialty().getCode()).isEqualTo("ENT");
        assertThat(r.getSpecialty().getId()).isEqualTo(ent.getId());
        assertThat(r.getSpecialty().getName()).isEqualTo(ent.getName());
        assertThat(r.getDisclaimer()).isEqualTo(TriageMessages.DISCLAIMER);
        assertThat(r.getDoctors()).extracting(TriageResponse.DoctorDto::getFullName)
                .containsExactly("Bác sĩ Sáng", "Bác sĩ Chiều", "An");
        assertThat(r.getDoctors().get(0).getNextSlot().getDate()).isEqualTo(tomorrow);
        assertThat(r.getDoctors().get(0).getNextSlot().getStartTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(r.getDoctors().get(2).getNextSlot()).isNull();
    }

    @Test
    void pass_withoutActiveDoctors_isReferred() {
        Specialty ent = entSpecialty(true);
        newDoctor(ent, "Bác sĩ Nghỉ", false);

        TriageResponse r = ask(null, sorethroat("ADULT"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.REFER);
        assertThat(r.getReasonCodes()).containsExactly("NO_DOCTOR");
        assertThat(r.getSpecialty()).isNull();
        assertThat(r.getDoctors()).isNull();
        assertThat(r.getDisclaimer()).isNull();
    }

    @Test
    void pass_specialtyMissingFromDatabase_isReferred() {
        TriageResponse r = ask(null, sorethroat("ADULT"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.REFER);
        assertThat(r.getReasonCodes()).containsExactly("SPECIALTY_NOT_FOUND");
    }

    @Test
    void pass_inactiveSpecialty_isReferred() {
        Specialty ent = entSpecialty(false);
        newDoctor(ent, "Bác sĩ", true);

        TriageResponse r = ask(null, sorethroat("ADULT"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.REFER);
        assertThat(r.getReasonCodes()).containsExactly("SPECIALTY_NOT_FOUND");
    }

    // ===== Nhóm tuổi từ hồ sơ bệnh nhân =====

    @Test
    void loggedIn_ageComesFromDateOfBirth_andAgeQuestionIsSkipped() {
        User user = newUser("bn", Role.PATIENT);
        Patient patient = patientOf(user, LocalDate.now().minusYears(30));
        TestSecurityHelper.loginAs(user);

        TriageResponse r = ask(patient.getId(), ans());

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.QUESTION);
        assertThat(r.getQuestion().getCode()).isEqualTo("Q_RED_FLAGS");
        assertThat(r.isAgeFromProfile()).isTrue();
    }

    @Test
    void loggedIn_dateOfBirthOverridesAgeSentByClient() {
        User user = newUser("bn", Role.PATIENT);
        Patient baby = patientOf(user, LocalDate.now().minusMonths(6));
        TestSecurityHelper.loginAs(user);

        // Client khai "người lớn" nhưng hồ sơ là trẻ 6 tháng → phải theo hồ sơ
        TriageResponse r = ask(baby.getId(), ans("AGE", "ADULT"));

        assertThat(r.getStatus()).isEqualTo(TriageFlowEngine.Status.REFER);
        assertThat(r.getReasonCodes()).contains("ESC_INFANT");
    }

    @Test
    void loggedIn_profileWithoutDateOfBirth_stillAsksAge() {
        User user = newUser("bn", Role.PATIENT);
        Patient patient = patientOf(user, null);
        TestSecurityHelper.loginAs(user);

        TriageResponse r = ask(patient.getId(), ans());

        assertThat(r.getQuestion().getCode()).isEqualTo("AGE");
        assertThat(r.isAgeFromProfile()).isFalse();
    }

    @Test
    void loggedIn_managedRelativeProfile_isUsableByItsManager() {
        User manager = newUser("bn", Role.PATIENT);
        Patient relative = patientRepository.save(Patient.builder().managedBy(manager)
                .fullName("Người thân").dateOfBirth(LocalDate.now().minusYears(70)).build());
        TestSecurityHelper.loginAs(manager);

        TriageResponse r = ask(relative.getId(), ans("Q_RED_FLAGS", "RF_NONE"));

        assertThat(r.isAgeFromProfile()).isTrue();
        assertThat(r.getQuestion().getCode()).isEqualTo("Q_COMPLAINT");
    }

    // ===== Quyền dùng hồ sơ =====

    @Test
    void someoneElsesProfile_isForbidden() {
        Patient theirs = patientOf(newUser("khac", Role.PATIENT), LocalDate.now().minusYears(30));
        TestSecurityHelper.loginAs(newUser("toi", Role.PATIENT));

        assertThatThrownBy(() -> ask(theirs.getId(), ans()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void nonExistentProfile_isForbiddenWithSameError() {
        TestSecurityHelper.loginAs(newUser("toi", Role.PATIENT));

        assertThatThrownBy(() -> ask(999_999L, ans()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("không có quyền");
    }

    @Test
    void anonymousAskingForAProfile_isForbidden() {
        Patient patient = patientOf(newUser("bn", Role.PATIENT), LocalDate.now().minusYears(30));

        assertThatThrownBy(() -> ask(patient.getId(), ans()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("đăng nhập");
    }

    @Test
    void usingTheFeature_writesNothing() {
        Specialty ent = entSpecialty(true);
        newDoctor(ent, "Bác sĩ", true);
        long patients = patientRepository.count();
        long slots = timeSlotRepository.count();

        ask(null, sorethroat("ADULT"));

        assertThat(patientRepository.count()).isEqualTo(patients);
        assertThat(timeSlotRepository.count()).isEqualTo(slots);
    }
}
