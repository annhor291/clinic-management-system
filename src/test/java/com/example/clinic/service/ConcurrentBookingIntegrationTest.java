package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.booking.engine.BookingRuleViolationException;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.Patient;
import com.example.clinic.entity.Specialty;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ConcurrentBookingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private AppointmentService appointmentService;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;

    @MockitoBean
    private EmailService emailService;

    private Long slotId;
    private User userA, userB;
    private Patient patientA, patientB;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);

        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Concurrent test").active(true).build());
        User doctorUser = userRepository.save(User.builder()
                .username("doc_conc").email("doc_conc@example.com").password("x")
                .role(Role.DOCTOR).provider(AuthProvider.LOCAL).enabled(true).build());
        Doctor doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser).specialty(specialty).fullName("BS Concurrent")
                .consultationFee(new BigDecimal("200000")).active(true).build());

        userA = newPatientUser("pa");
        userB = newPatientUser("pb");
        patientA = patientRepository.save(Patient.builder().user(userA).fullName("Benh nhan A").build());
        patientB = patientRepository.save(Patient.builder().user(userB).fullName("Benh nhan B").build());

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        doctorScheduleService.createFromApprovedRegistration(
                doctor.getId(), tomorrow, LocalTime.of(8, 0), LocalTime.of(9, 0), ShiftType.MORNING);
        slotId = timeSlotRepository.findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                doctor.getId(), tomorrow, SlotStatus.AVAILABLE).get(0).getId();
    }

    private User newPatientUser(String name) {
        return userRepository.save(User.builder()
                .username(name + "_conc").email(name + "_conc@example.com").password("x")
                .role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());
    }

    @RepeatedTest(5)
    void twoPatientsBookSameSlot_exactlyOneWins() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        List<Future<Throwable>> futures = new ArrayList<>();
        for (var pair : List.of(java.util.Map.entry(userA, patientA), java.util.Map.entry(userB, patientB))) {
            futures.add(pool.submit(() -> {
                try {
                    TestSecurityHelper.loginAs(pair.getKey());
                    ready.countDown();
                    go.await();
                    appointmentService.book(AppointmentRequest.builder()
                            .patientId(pair.getValue().getId()).timeSlotId(slotId).build());
                    return null;                       // đặt thành công
                } catch (Throwable t) {
                    return t;                          // đặt thất bại
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
        }
        ready.await();
        go.countDown();

        List<Throwable> errors = new ArrayList<>();
        for (Future<Throwable> f : futures) {
            Throwable t = f.get(30, TimeUnit.SECONDS);
            if (t != null) errors.add(t);
        }
        pool.shutdown();

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).isInstanceOf(BookingRuleViolationException.class);
        assertThat(appointmentRepository.findAll()).hasSize(1);
        assertThat(timeSlotRepository.findById(slotId).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
    }
}
