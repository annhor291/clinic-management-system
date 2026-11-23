package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.PatientCreateRequest;
import com.example.clinic.dto.request.PatientUpdateRequest;
import com.example.clinic.dto.response.PatientResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class PatientSelfServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PatientService patientService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;

    @MockitoBean
    private EmailService emailService;

    private User patientUser;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);
        patientUser = userRepository.save(User.builder()
                .username("patient_self_test").email("patient_self_test@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    private PatientCreateRequest createRequest(String name, String phone) {
        PatientCreateRequest r = new PatientCreateRequest();
        r.setFullName(name);
        r.setPhone(phone);
        return r;
    }

    @Test
    void createMe_shouldCreateProfileLinkedToCurrentUser() {
        TestSecurityHelper.loginAs(patientUser);

        PatientResponse res = patientService.createMe(createRequest("Nguyen Van A", "0912345678"));

        assertThat(res.getFullName()).isEqualTo("Nguyen Van A");
        assertThat(patientRepository.findByUserId(patientUser.getId())).isPresent();
    }

    @Test
    void createMe_twice_shouldBeRejected() {
        TestSecurityHelper.loginAs(patientUser);
        patientService.createMe(createRequest("Nguyen Van A", "0912345678"));

        assertThatThrownBy(() -> patientService.createMe(createRequest("Nguyen Van B", "0912345679")))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateMe_shouldChangeOnlyOwnProfile() {
        TestSecurityHelper.loginAs(patientUser);
        patientService.createMe(createRequest("Nguyen Van A", "0912345678"));

        User other = userRepository.save(User.builder()
                .username("patient_other").email("patient_other@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
        Patient otherProfile = patientRepository.save(
                Patient.builder().user(other).fullName("Nguoi khac").phone("0900000000").build());

        PatientUpdateRequest update = new PatientUpdateRequest();
        update.setPhone("0987654321");
        PatientResponse res = patientService.updateMe(update);

        assertThat(res.getPhone()).isEqualTo("0987654321");
        assertThat(res.getFullName()).isEqualTo("Nguyen Van A");   // field null trong request thì giữ nguyên
        assertThat(patientRepository.findById(otherProfile.getId()).orElseThrow().getPhone())
                .isEqualTo("0900000000");
    }

    @Test
    void updateMe_withoutProfile_shouldThrowNotFound() {
        TestSecurityHelper.loginAs(patientUser);

        assertThatThrownBy(() -> patientService.updateMe(new PatientUpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_shouldBeRejected_whenPatientHasAppointment() {
        Patient patient = patientRepository.save(
                Patient.builder().user(patientUser).fullName("BN co lich").build());
        bookAppointmentFor(patient);

        assertThatThrownBy(() -> patientService.delete(patient.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đã có lịch hẹn");
        assertThat(patientRepository.findById(patient.getId())).isPresent();
    }

    @Test
    void delete_shouldWork_whenPatientHasNoAppointment() {
        Patient patient = patientRepository.save(
                Patient.builder().user(patientUser).fullName("BN khong lich").build());

        patientService.delete(patient.getId());

        assertThat(patientRepository.findById(patient.getId())).isEmpty();
    }

    private void bookAppointmentFor(Patient patient) {
        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Noi khoa - patient test").active(true).build());
        User doctorUser = userRepository.save(User.builder()
                .username("doctor_self_test").email("doctor_self_test@example.com")
                .password("x").role(Role.DOCTOR).provider(AuthProvider.LOCAL).enabled(true).build());
        Doctor doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser).specialty(specialty).fullName("BS Self Test")
                .consultationFee(new BigDecimal("200000")).active(true).build());

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0), ShiftType.MORNING);
        TimeSlot slot = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), tomorrow, SlotStatus.AVAILABLE).get(0);

        TestSecurityHelper.loginAs(patientUser);
        appointmentService.book(AppointmentRequest.builder()
                .patientId(patient.getId()).timeSlotId(slot.getId()).build());
    }
}
