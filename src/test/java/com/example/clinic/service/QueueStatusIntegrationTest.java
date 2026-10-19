package com.example.clinic.service;

import com.example.clinic.BaseIntegrationTest;
import com.example.clinic.TestSecurityHelper;
import com.example.clinic.dto.response.QueueStatusResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.*;
import com.example.clinic.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Lưu ý: test này KHÔNG dùng appointmentService.book() để tạo dữ liệu, vì book() cố tình
// chặn đặt lịch cho giờ đã qua (BookingWindowRule) — đúng hành vi khi dùng qua API thật.
// Ở đây ta cần giả lập 1 appointment "đã tồn tại từ trước, giờ hẹn đã tới" để test complete()/
// getQueueStatus(), nên persist thẳng entity qua repository, bỏ qua tầng Service cho bước setup.
public class QueueStatusIntegrationTest extends BaseIntegrationTest {

    @Autowired private AppointmentService appointmentService;
    @Autowired private DoctorScheduleService doctorScheduleService;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private SpecialtyRepository specialtyRepository;
    @Autowired private DoctorScheduleRepository scheduleRepository;
    @Autowired private TimeSlotRepository timeSlotRepository;
    @Autowired private AppointmentRepository appointmentRepository;

    @MockitoBean
    private EmailService emailService;

    private Doctor doctor;
    private Patient patient;
    private DoctorSchedule schedule;

    private User adminUser;

    @BeforeEach
    void setUp() {
        when(emailService.sendEmailSync(any(), any(), any())).thenReturn(true);

        Specialty specialty = specialtyRepository.save(
                Specialty.builder().name("Da liễu - test").active(true).build());

        adminUser = userRepository.save(User.builder()
                .username("admin_queue_test").email("admin_queue_test@example.com")
                .password("x").role(Role.ADMIN).provider(AuthProvider.LOCAL).enabled(true).build());

        User doctorUser = userRepository.save(User.builder()
                .username("doctor_queue_test").email("doctor_queue_test@example.com")
                .password("x").role(Role.DOCTOR).provider(AuthProvider.LOCAL).enabled(true).build());

        doctor = doctorRepository.save(Doctor.builder()
                .user(doctorUser).specialty(specialty).fullName("BS Da liễu Test").active(true).build());

        User patientUser = userRepository.save(User.builder()
                .username("patient_queue_test").email("patient_queue_test@example.com")
                .password("x").role(Role.PATIENT).provider(AuthProvider.LOCAL).enabled(true).build());

        patient = patientRepository.save(Patient.builder()
                .user(patientUser).fullName("Bệnh nhân Queue Test").build());

        // Ca làm việc "hôm nay", bắt đầu từ 10 phút trước — mô phỏng ca đang diễn ra
        LocalTime shiftStart = LocalTime.now().minusMinutes(10);
        schedule = scheduleRepository.save(DoctorSchedule.builder()
                .doctor(doctor)
                .workDate(LocalDate.now())
                .startTime(shiftStart)
                .endTime(shiftStart.plusHours(2))
                .slotDurationMinutes(30)
                .shiftType(ShiftType.CUSTOM)
                .active(true)
                .build());

        // 3 slot liên tiếp, queueNumber 1-2-3, slot đầu đã BOOKED (đã qua giờ)
        for (int i = 0; i < 3; i++) {
            TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                    .schedule(schedule)
                    .doctor(doctor)
                    .slotDate(LocalDate.now())
                    .startTime(shiftStart.plusMinutes(30L * i))
                    .endTime(shiftStart.plusMinutes(30L * (i + 1)))
                    .status(i == 0 ? SlotStatus.BOOKED : SlotStatus.AVAILABLE)
                    .queueNumber(i + 1)
                    .build());

            if (i == 0) {
                appointmentRepository.save(Appointment.builder()
                        .bookingCode("APT-TEST-" + UUID.randomUUID().toString().substring(0, 6))
                        .patient(patient)
                        .doctor(doctor)
                        .timeSlot(slot)
                        .appointmentTime(LocalDateTime.of(LocalDate.now(), slot.getStartTime()))
                        .status(AppointmentStatus.CONFIRMED)
                        .build());
            }
        }
    }

    @Test
    void complete_shouldAdvanceCurrentServingNumber() {
        Appointment firstAppointment = appointmentRepository.findAll().stream()
                .filter(a -> a.getDoctor().getId().equals(doctor.getId()))
                .findFirst().orElseThrow();

        QueueStatusResponse before = doctorScheduleService.getQueueStatus(schedule.getId());
        assertThat(before.getCurrentServingNumber()).isNull(); // chưa ai complete

        TestSecurityHelper.loginAs(adminUser);
        appointmentService.complete(firstAppointment.getId());

        QueueStatusResponse after = doctorScheduleService.getQueueStatus(schedule.getId());
        assertThat(after.getCurrentServingNumber()).isEqualTo(2); // đã xong số 1, đang tới số 2
    }

    @Test
    void uncompletedPastSlot_shouldBeMarkedDelayed() {
        QueueStatusResponse status = doctorScheduleService.getQueueStatus(schedule.getId());
        assertThat(status.isDelayed()).isTrue();
    }
}
