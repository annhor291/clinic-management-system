package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.ManagedPatientCreateRequest;
import com.example.clinic.dto.response.PatientResponse;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.Specialty;
import com.example.clinic.entity.TimeSlot;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.*;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

public class ManagedPatientIntegrationTest extends BaseIntegrationTest {

    @Autowired private PatientService patientService;
    @Autowired private AppointmentService appointmentService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private NotificationRepository notificationRepository;

    @MockitoBean
    private EmailService emailService;

    private User ownerUser;
    private User otherUser;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);
        doNothing().when(emailService).sendEmail(any(), any(), any());

        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Nhi khoa - test").active(true).build());

        User doctorUser = userRepository.save(User.builder()
                .username("doctor_managed_test").email("doctor_managed_test@example.com")
                .password("x").role(Role.DOCTOR).provider(AuthProvider.LOCAL).enabled(true).build());

        doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser).specialty(specialty).fullName("BS Nhi Test").active(true).build());

        ownerUser = userRepository.save(User.builder()
                .username("owner_test").email("owner_test@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());

        otherUser = userRepository.save(User.builder()
                .username("other_test").email("other_test@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());

        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(),
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                ShiftType.MORNING);
    }

    @Test
    void createManaged_thenBookForThem_shouldSucceed() {
        TestSecurityHelper.loginAs(ownerUser);

        PatientResponse managedPatient = patientService.createManaged(
                ManagedPatientCreateRequest.builder()
                        .fullName("Con của owner")
                        .relationshipLabel("Con")
                        .build());

        assertThat(managedPatient.isManaged()).isTrue();

        TimeSlot slot = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), LocalDate.now().plusDays(1), SlotStatus.AVAILABLE).get(0);

        var response = appointmentService.book(AppointmentRequest.builder()
                .patientId(managedPatient.getId())
                .timeSlotId(slot.getId())
                .build());

        assertThat(response.getStatus()).isEqualTo(AppointmentStatus.PENDING);

        await().atMost(5, SECONDS).untilAsserted(() ->
                assertThat(notificationRepository.findByUserIdOrderByCreatedAtDesc(
                        ownerUser.getId(), PageRequest.of(0, 5)).getContent())
                        .anyMatch(n -> n.getType() == NotificationType.APPOINTMENT_BOOKED));
    }

    @Test
    void bookForSomeoneElsesManagedPatient_shouldBeDenied() {
        TestSecurityHelper.loginAs(ownerUser);
        PatientResponse managedPatient = patientService.createManaged(
                ManagedPatientCreateRequest.builder().fullName("Con của owner 2").build());

        // otherUser KHÔNG phải người quản lý hồ sơ này — thử đặt lịch cho hồ sơ đó
        TestSecurityHelper.loginAs(otherUser);

        TimeSlot slot = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), LocalDate.now().plusDays(1), SlotStatus.AVAILABLE).get(0);

        assertThatThrownBy(() -> appointmentService.book(AppointmentRequest.builder()
                .patientId(managedPatient.getId())
                .timeSlotId(slot.getId())
                .build()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void exceedingManagedPatientLimit_shouldThrow() {
        TestSecurityHelper.loginAs(ownerUser);

        for (int i = 0; i < 10; i++) {
            patientService.createManaged(ManagedPatientCreateRequest.builder()
                    .fullName("Người thân " + i).build());
        }

        assertThatThrownBy(() -> patientService.createManaged(
                ManagedPatientCreateRequest.builder().fullName("Người thân thứ 11").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("giới hạn");
    }

}
