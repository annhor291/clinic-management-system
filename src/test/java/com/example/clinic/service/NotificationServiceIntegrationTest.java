package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.*;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;


import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.*;

public class NotificationServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private AuthService authService;

    // Mock EmailService — không gửi email thật, chỉ verify nó được gọi đúng cách
    @MockitoBean private EmailService emailService;

    private User patientUser;
    private Patient patient;
    private Doctor doctor;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);

        // Tạo dữ liệu nền: 1 chuyên khoa, 1 bác sĩ, 1 bệnh nhân, 1 slot
        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Nội khoa - test").active(true).build());

        User doctorUser = userRepository.save(User.builder()
                .username("doctor_test").email("doctor_test@example.com")
                .password("x").role(Role.DOCTOR).provider(AuthProvider.LOCAL).enabled(true).build());

        doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser).specialty(specialty).fullName("BS Test")
                .active(true).build());

        patientUser = userRepository.save(User.builder()
                .username("patient_test").email("patient_test@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());

        patient = patientRepository.save(Patient.builder()
                .user(patientUser).fullName("Bệnh nhân Test").build());

        // Tạo 1 ca làm việc cho ngày mai, có slot cách hiện tại hơn 1 phút (đủ qua BookingWindowRule
        // đã rút gọn trong application-test.properties)
        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(),
                LocalDate.now().plusDays(1),
                LocalTime.of(8, 0),
                LocalTime.of(9, 0),
                ShiftType.MORNING);
    }

    @Test
    void book_shouldSendBookedNotification() {
        TestSecurityHelper.loginAs(patientUser);

        TimeSlot slot = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), LocalDate.now().plusDays(1), SlotStatus.AVAILABLE).get(0);

        appointmentService.book(AppointmentRequest.builder()
                .patientId(patient.getId())
                .timeSlotId(slot.getId())
                .note("test booking")
                .build());

        verify(emailService, timeout(5000)).sendEmailSync(
                eq(patientUser.getEmail()),
                contains("Đặt lịch khám thành công"),
                any());

        await().atMost(5, SECONDS).untilAsserted(() -> {
            var notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                    patientUser.getId(), PageRequest.of(0, 5)).getContent();
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.APPOINTMENT_BOOKED);
            assertThat(notifications.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        });
    }

    @Test
    void clinicCancel_shouldPutLinkInEmail_butNotInAppContent() {
        TestSecurityHelper.loginAs(patientUser);
        TimeSlot slot = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), LocalDate.now().plusDays(1), SlotStatus.AVAILABLE).get(0);
        AppointmentResponse booked = appointmentService.book(AppointmentRequest.builder()
                .patientId(patient.getId()).timeSlotId(slot.getId()).build());

        User admin = userRepository.save(User.builder()
                .username("admin_notif").email("admin_notif@example.com")
                .password("x").role(Role.ADMIN).provider(AuthProvider.LOCAL).enabled(true).build());
        TestSecurityHelper.loginAs(admin);
        appointmentService.cancelByClinic(booked.getId(), "Bác sĩ nghỉ đột xuất");

        verify(emailService, timeout(5000)).sendEmailSync(
                eq(patientUser.getEmail()),
                contains("Phòng khám đã hủy lịch hẹn"),
                contains("/appointments/" + booked.getId()));

        await().atMost(5, SECONDS).untilAsserted(() -> {
            var list = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                    patientUser.getId(), PageRequest.of(0, 5)).getContent();
            var cancelled = list.stream()
                    .filter(n -> n.getType() == NotificationType.APPOINTMENT_CANCELLED)
                    .findFirst().orElseThrow();
            assertThat(cancelled.getContent()).contains("đặt lịch tương tự").doesNotContain("<a href");
            assertThat(cancelled.getAppointment().getId()).isEqualTo(booked.getId());
        });
    }

    @Test
    void notifyWelcome_shouldCreateWelcomeNotification_andSendEmail() {
        notificationService.notifyWelcome(patientUser.getId());

        verify(emailService, timeout(5000)).sendEmailSync(
                eq(patientUser.getEmail()), contains("Chào mừng"), any());

        var list = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                patientUser.getId(), PageRequest.of(0, 5)).getContent();
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getType()).isEqualTo(NotificationType.WELCOME);
        assertThat(list.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(list.get(0).getAppointment()).isNull();
    }

    @Test
    void verifyEmail_shouldTriggerWelcomeNotification() {
        User newcomer = userRepository.save(User.builder()
                .username("newcomer").email("newcomer@example.com").password("x")
                .role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true)
                .emailVerified(false)
                .verificationToken("tok-welcome")
                .verificationTokenExpiry(LocalDateTime.now().plusHours(1))
                .build());

        authService.verifyEmail("tok-welcome");

        await().atMost(5, SECONDS).untilAsserted(() -> {
            var list = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                    newcomer.getId(), PageRequest.of(0, 5)).getContent();
            assertThat(list).extracting(n -> n.getType()).contains(NotificationType.WELCOME);
        });
    }
}
