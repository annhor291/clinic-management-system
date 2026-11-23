package com.example.clinic.config;

import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.Patient;
import com.example.clinic.entity.Specialty;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuthProvider;
import com.example.clinic.entity.enums.Gender;
import com.example.clinic.entity.enums.Role;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.SpecialtyRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.service.DoctorScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

// Nạp dữ liệu mẫu cho demo và QA. Chỉ chạy khi app.seed.enabled=true (mặc định tắt).
// Chạy lại nhiều lần vẫn an toàn: tài khoản đã có thì bỏ qua.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final String[][] SPECIALTIES = {
            {"Nội khoa", "Khám và điều trị các bệnh nội khoa thường gặp"},
            {"Tim mạch", "Khám và điều trị các bệnh về tim và mạch máu"},
            {"Da liễu", "Khám và điều trị các bệnh về da, tóc, móng"},
            {"Nhi khoa", "Khám và chăm sóc sức khỏe trẻ em"},
            {"Thần kinh", "Khám và điều trị các bệnh về hệ thần kinh"},
            {"Xương khớp", "Khám và điều trị các bệnh cơ, xương, khớp"},
            {"Tiêu hóa", "Khám và điều trị các bệnh dạ dày, ruột, gan, mật"},
            {"Hô hấp", "Khám và điều trị các bệnh phổi và đường hô hấp"},
            {"Tai mũi họng", "Khám và điều trị các bệnh tai, mũi, họng"},
            {"Mắt", "Khám và điều trị các bệnh về mắt"},
            {"Răng hàm mặt", "Khám và điều trị các bệnh về răng, nướu, hàm mặt"}
    };

    // email, họ tên, chức danh, chuyên khoa, số năm kinh nghiệm, số điện thoại, phí khám
    private static final Object[][] DOCTORS = {
            {"bs.nam@clinic.local", "Nguyễn Văn Nam", "BS", "Nội khoa", 12, "0901000001", 150000},
            {"bs.ha@clinic.local", "Đặng Thu Hà", "BS", "Nội khoa", 7, "0901000002", 150000},
            {"bs.lan@clinic.local", "Trần Thị Lan", "ThS.BS", "Nhi khoa", 8, "0901000003", 180000},
            {"bs.huy@clinic.local", "Lê Quốc Huy", "TS.BS", "Tim mạch", 15, "0901000004", 300000},
            {"bs.chau@clinic.local", "Phạm Minh Châu", "BS", "Da liễu", 6, "0901000005", 200000},
            {"bs.bao@clinic.local", "Hoàng Gia Bảo", "ThS.BS", "Tiêu hóa", 10, "0901000006", 250000},
            {"bs.tam@clinic.local", "Võ Thanh Tâm", "BS", "Tai mũi họng", 9, "0901000007", 200000},
            {"bs.khoa@clinic.local", "Ngô Anh Khoa", "TS.BS", "Thần kinh", 14, "0901000008", 280000},
            {"bs.duc@clinic.local", "Bùi Minh Đức", "ThS.BS", "Xương khớp", 11, "0901000009", 230000},
            {"bs.yen@clinic.local", "Lý Hải Yến", "BS", "Hô hấp", 8, "0901000010", 200000},
            {"bs.mai@clinic.local", "Đinh Thu Mai", "BS", "Mắt", 9, "0901000011", 200000},
            {"bs.phong@clinic.local", "Trương Quốc Phong", "BS", "Răng hàm mặt", 10, "0901000012", 220000}
    };

    private final SpecialtyRepository specialtyRepository;
    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorScheduleService doctorScheduleService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.password:Demo@12345}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, Specialty> specialties = seedSpecialties();
        seedStaff();
        seedDoctors(specialties);
        seedPatients();
        log.info("Đã nạp dữ liệu mẫu. Mật khẩu chung của các tài khoản mẫu là giá trị app.seed.password");
    }

    private Map<String, Specialty> seedSpecialties() {
        Map<String, Specialty> result = new HashMap<>();
        for (String[] s : SPECIALTIES) {
            Specialty specialty = specialtyRepository.findByName(s[0])
                    .orElseGet(() -> specialtyRepository.save(
                            Specialty.builder().name(s[0]).description(s[1]).build()));
            result.put(s[0], specialty);
        }
        return result;
    }

    private void seedStaff() {
        createUserIfAbsent("admin@clinic.local", "Quản trị viên", Role.ADMIN);
        createUserIfAbsent("letan@clinic.local", "Lễ tân Hoa", Role.RECEPTIONIST);
    }

    private void seedDoctors(Map<String, Specialty> specialties) {
        for (Object[] d : DOCTORS) {
            String email = (String) d[0];
            if (userRepository.existsByEmail(email)) continue;

            User user = createUserIfAbsent(email, (String) d[1], Role.DOCTOR);
            Doctor doctor = doctorRepository.save(Doctor.builder()
                    .user(user)
                    .specialty(specialties.get((String) d[3]))
                    .fullName((String) d[1])
                    .title((String) d[2])
                    .experienceYears((Integer) d[4])
                    .phone((String) d[5])
                    .consultationFee(BigDecimal.valueOf((Integer) d[6]))
                    .build());
            seedSchedules(doctor);
        }
    }

    private void seedPatients() {
        createPatientIfAbsent("benhnhan1@clinic.local", "Nguyễn Minh Anh", Gender.FEMALE,
                "0912000001", LocalDate.of(1998, 5, 12));
        createPatientIfAbsent("benhnhan2@clinic.local", "Trần Quốc Bảo", Gender.MALE,
                "0912000002", LocalDate.of(1985, 11, 3));
        createPatientIfAbsent("benhnhan3@clinic.local", "Lê Thị Cẩm", Gender.FEMALE,
                "0912000003", LocalDate.of(1972, 8, 21));
    }

    // 14 ngày tới: T2-T6 sáng và chiều, T7 chỉ sáng, CN nghỉ
    private void seedSchedules(Doctor doctor) {
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 14; i++) {
            LocalDate date = today.plusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            if (dow == DayOfWeek.SUNDAY) continue;

            doctorScheduleService.createFromApprovedRegistration(
                    doctor.getId(), date, LocalTime.of(8, 0), LocalTime.of(12, 0), ShiftType.MORNING);
            if (dow != DayOfWeek.SATURDAY) {
                doctorScheduleService.createFromApprovedRegistration(
                        doctor.getId(), date, LocalTime.of(13, 0), LocalTime.of(17, 0), ShiftType.AFTERNOON);
            }
        }
    }

    private User createUserIfAbsent(String email, String username, Role role) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .username(username)
                .password(passwordEncoder.encode(seedPassword))
                .role(role)
                .provider(AuthProvider.LOCAL)
                .enabled(true)
                .build()));
    }

    private void createPatientIfAbsent(String email, String fullName, Gender gender,
                                       String phone, LocalDate dateOfBirth) {
        if (userRepository.existsByEmail(email)) return;
        User user = createUserIfAbsent(email, fullName, Role.PATIENT);
        patientRepository.save(Patient.builder()
                .user(user)
                .fullName(fullName)
                .gender(gender)
                .phone(phone)
                .dateOfBirth(dateOfBirth)
                .build());
    }


}
