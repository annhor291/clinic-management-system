package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Kiểm tra quyền riêng tư khi xem danh sách lịch hẹn:
//  - bệnh nhân không được xem danh sách lịch hẹn của bác sĩ
//  - bác sĩ không được xem danh sách lịch hẹn của bệnh nhân
//  - bác sĩ chỉ xem được lịch của chính mình
public class AppointmentListPrivacyIntegrationTest extends BaseIntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired
    private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;

    private User adminUser, patientUser, doctorUser, otherDoctorUser;
    private Doctor doctor, otherDoctor;
    private Patient patient;

    @BeforeEach
    void setUp() {
        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Privacy test").active(true).build());

        adminUser = user("admin_pv", Role.ADMIN);
        patientUser = user("patient_pv", Role.PATIENT);
        doctorUser = user("doctor_pv", Role.DOCTOR);
        otherDoctorUser = user("doctor2_pv", Role.DOCTOR);

        doctor = doctorRepository.save(Doctor.builder().user(doctorUser).specialty(specialty)
                .fullName("BS Mot").consultationFee(new BigDecimal("200000")).active(true).build());
        otherDoctor = doctorRepository.save(Doctor.builder().user(otherDoctorUser).specialty(specialty)
                .fullName("BS Hai").consultationFee(new BigDecimal("200000")).active(true).build());
        patient = patientRepository.save(
                Patient.builder().user(patientUser).fullName("Benh nhan PV").build());
    }

    private User user(String name, Role role) {
        return userRepository.save(User.builder().username(name).email(name + "@example.com")
                .password("x").role(role).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    @Test
    void patient_cannotListDoctorAppointments() {
        TestSecurityHelper.loginAs(patientUser);

        assertThatThrownBy(() -> appointmentService.getByDoctor(doctor.getId(), 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctor_cannotListPatientAppointments() {
        TestSecurityHelper.loginAs(doctorUser);

        assertThatThrownBy(() -> appointmentService.getByPatient(patient.getId(), 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctor_cannotListAnotherDoctorsAppointments() {
        TestSecurityHelper.loginAs(doctorUser);

        assertThatThrownBy(() -> appointmentService.getByDoctor(otherDoctor.getId(), 0, 10))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void doctor_canListOwnAppointments() {
        TestSecurityHelper.loginAs(doctorUser);

        assertThat(appointmentService.getByDoctor(doctor.getId(), 0, 10)).isNotNull();
    }

    @Test
    void patient_canListOwnAppointments() {
        TestSecurityHelper.loginAs(patientUser);

        assertThat(appointmentService.getByPatient(patient.getId(), 0, 10)).isNotNull();
    }

    @Test
    void admin_canListBoth() {
        TestSecurityHelper.loginAs(adminUser);

        assertThat(appointmentService.getByDoctor(doctor.getId(), 0, 10)).isNotNull();
        assertThat(appointmentService.getByPatient(patient.getId(), 0, 10)).isNotNull();
    }

    @Autowired private PatientService patientService;

    @Test
    void doctor_cannotViewPatientWithoutAppointment() {
        TestSecurityHelper.loginAs(doctorUser);

        assertThatThrownBy(() -> patientService.getById(patient.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void admin_canViewAnyPatient() {
        TestSecurityHelper.loginAs(adminUser);

        assertThat(patientService.getById(patient.getId())).isNotNull();
    }
}
