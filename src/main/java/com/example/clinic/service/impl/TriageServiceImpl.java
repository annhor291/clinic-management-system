package com.example.clinic.service.impl;

import com.example.clinic.booking.config.BookingProperties;
import com.example.clinic.config.ClinicProperties;
import com.example.clinic.dto.request.TriageRequest;
import com.example.clinic.dto.response.TriageResponse;
import com.example.clinic.entity.*;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.SpecialtyRepository;
import com.example.clinic.repository.TimeSlotRepository;
import com.example.clinic.service.TriageService;
import com.example.clinic.triage.AgeGroup;
import com.example.clinic.triage.TriageMessages;
import com.example.clinic.triage.TriageProperties;
import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.engine.TriageFlowEngine;
import com.example.clinic.triage.rules.TriageRules;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TriageServiceImpl implements TriageService {

    private static final String SELF_HARM_RULE = "ESC_SELF_HARM";

    private final TriageFlowEngine engine;
    private final TriageRuleBook ruleBook;
    private final TriageProperties properties;
    private final ClinicProperties clinicProperties;
    private final BookingProperties bookingProperties;
    private final PatientRepository patientRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final TimeSlotRepository timeSlotRepository;

    // readOnly: tính năng này chỉ ĐỌC (hồ sơ, bác sĩ, lịch trống), tuyệt đối không ghi gì
    @Override
    @Transactional(readOnly = true)
    public TriageResponse next(TriageRequest request) {
        Map<String, List<String>> answers = new LinkedHashMap<>(request.getAnswers());
        boolean ageFromProfile = applyAgeFromProfile(request.getPatientId(), answers);

        TriageFlowEngine.TriageStep step = engine.next(answers);

        TriageResponse.TriageResponseBuilder base = TriageResponse.builder()
                .status(step.status())
                .ageFromProfile(ageFromProfile)
                .emergencyNumber(properties.getEmergencyNumber())
                .clinicPhone(clinicProperties.getPhone());

        return switch (step.status()) {
            case QUESTION -> base.question(toQuestionDto(step.question())).build();
            case EMERGENCY -> emergency(base, step);
            case REFER -> refer(base, step);
            case PASS -> pass(base, step);
        };
    }

    // ===== Nhóm tuổi tự động từ hồ sơ =====

    // Trả true nếu đã điền AGE từ ngày sinh. Hồ sơ không có (hoặc ngày sinh không hợp lệ) thì để client trả lời câu AGE.
    // AGE từ hồ sơ LUÔN ghi đè AGE client gửi, vì ngày sinh đáng tin hơn.
    private boolean applyAgeFromProfile(Long patientId, Map<String, List<String>> answers) {
        if (patientId == null) {
            return false;
        }
        Long userId = requireLoggedInUserId();

        // Không tồn tại và không phải của mình cho CÙNG một thông báo: không để dò xem hồ sơ nào có thật
        Patient patient = patientRepository.findById(patientId)
                .filter(p -> p.isOwnedByUser(userId))
                .orElseThrow(() -> new AccessDeniedException("Bạn không có quyền dùng hồ sơ bệnh nhân này"));

        Optional<String> group = AgeGroup.of(patient.getDateOfBirth(), LocalDate.now());
        group.ifPresent(g -> answers.put("AGE", List.of(g)));
        return group.isPresent();
    }

    // Endpoint công khai nên người chưa đăng nhập vẫn tới được đây; chỉ khi xin dùng hồ sơ mới đòi đăng nhập
    private Long requireLoggedInUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("Cần đăng nhập để dùng thông tin từ hồ sơ bệnh nhân");
        }
        return user.getId();
    }

    // ===== Các loại kết quả =====

    private TriageResponse emergency(TriageResponse.TriageResponseBuilder base, TriageFlowEngine.TriageStep step) {
        boolean selfHarm = step.escalations().stream().anyMatch(r -> SELF_HARM_RULE.equals(r.id()));
        boolean otherDanger = step.escalations().stream().anyMatch(r -> !SELF_HARM_RULE.equals(r.id()));
        String number = properties.getEmergencyNumber();

        if (selfHarm && otherDanger) {
            // Cả hai cùng lúc: nhắc cấp cứu trước, kèm lời hỗ trợ
            return base.message(TriageMessages.emergency(number))
                    .supportMessage(TriageMessages.selfHarm(number))
                    .build();
        }
        return base.message(selfHarm ? TriageMessages.selfHarm(number) : TriageMessages.emergency(number)).build();
    }

    private TriageResponse refer(TriageResponse.TriageResponseBuilder base, TriageFlowEngine.TriageStep step) {
        List<String> reasons;
        List<String> codes;
        if (!step.escalations().isEmpty()) {
            reasons = step.escalations().stream().map(TriageRules.EscalationRule::reason).toList();
            codes = step.escalations().stream().map(TriageRules.EscalationRule::id).toList();
        } else {
            reasons = List.of(TriageMessages.LOW_CONFIDENCE_REASON);
            codes = List.of(step.gate().reason());
        }
        return base.message(TriageMessages.refer(clinicProperties.getPhone(), properties.getEmergencyNumber()))
                .reasons(reasons)
                .reasonCodes(codes)
                .build();
    }

    private TriageResponse pass(TriageResponse.TriageResponseBuilder base, TriageFlowEngine.TriageStep step) {
        String code = step.gate().specialtyCode();
        String name = ruleBook.rules().specialties().get(code);

        Optional<Specialty> specialty = name == null
                ? Optional.empty()
                : specialtyRepository.findByName(name).filter(Specialty::isActive);
        if (specialty.isEmpty()) {
            return referNoDoctor(base, "SPECIALTY_NOT_FOUND");
        }

        List<Doctor> doctors = doctorRepository.findBySpecialtyIdAndActiveTrue(specialty.get().getId());
        if (doctors.isEmpty()) {
            return referNoDoctor(base, "NO_DOCTOR");
        }

        return base.message(TriageMessages.pass(name))
                .specialty(TriageResponse.SpecialtyDto.builder().id(specialty.get().getId()).code(code).name(name).build())
                .doctors(toDoctorDtos(doctors))
                .disclaimer(TriageMessages.DISCLAIMER)
                .build();
    }

    // Có kết quả PASS nhưng phòng khám chưa có bác sĩ để gợi ý → chuyển nhân viên, không để người dùng "đứng giữa đường"
    private TriageResponse referNoDoctor(TriageResponse.TriageResponseBuilder base, String reasonCode) {
        return base.status(TriageFlowEngine.Status.REFER)
                .message(TriageMessages.noDoctor(clinicProperties.getPhone(), properties.getEmergencyNumber()))
                .reasons(List.of(TriageMessages.LOW_CONFIDENCE_REASON))
                .reasonCodes(List.of(reasonCode))
                .build();
    }

    // ===== Bác sĩ và lịch trống gần nhất =====

    // Bác sĩ cùng giờ trống sớm nhất, để sắp xếp mà không cần đưa trường thời gian vào DTO
    private record Candidate(TriageResponse.DoctorDto dto, LocalDateTime nextStart) {}

    private List<TriageResponse.DoctorDto> toDoctorDtos(List<Doctor> doctors) {
        // Cùng quy tắc "đặt trước tối thiểu" như lúc đặt lịch thật, để slot gợi ý chắc chắn đặt được
        LocalDateTime earliest = LocalDateTime.now().plusMinutes(bookingProperties.getMinLeadTimeMinutes());
        LocalDate toDate = earliest.toLocalDate().plusDays(properties.getLookAheadDays());

        List<Candidate> candidates = new ArrayList<>();
        for (Doctor d : doctors) {
            Optional<TimeSlot> next = timeSlotRepository
                    .findNextAvailable(d.getId(), earliest.toLocalDate(), earliest.toLocalTime(), toDate,
                            PageRequest.of(0, 1))
                    .stream().findFirst();

            TriageResponse.DoctorDto dto = TriageResponse.DoctorDto.builder()
                    .id(d.getId())
                    .fullName(d.getFullName())
                    .title(d.getTitle())
                    .experienceYears(d.getExperienceYears())
                    .consultationFee(d.getConsultationFee())
                    .nextSlot(next.map(this::toSlotDto).orElse(null))
                    .build();
            candidates.add(new Candidate(dto, next
                    .map(s -> LocalDateTime.of(s.getSlotDate(), s.getStartTime())).orElse(null)));
        }

        // Có lịch trống sớm nhất lên đầu; chưa có lịch trống xuống cuối (xếp theo tên)
        candidates.sort(Comparator
                .comparing(Candidate::nextStart, Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder()))
                .thenComparing(c -> c.dto().getFullName()));
        return candidates.stream().map(Candidate::dto).toList();
    }

    private TriageResponse.NextSlotDto toSlotDto(TimeSlot s) {
        return TriageResponse.NextSlotDto.builder()
                .slotId(s.getId())
                .date(s.getSlotDate())
                .startTime(s.getStartTime())
                .endTime(s.getEndTime())
                .build();
    }

    // ===== Câu hỏi =====

    private TriageResponse.QuestionDto toQuestionDto(TriageRules.Question q) {
        return TriageResponse.QuestionDto.builder()
                .code(q.code())
                .text(q.text())
                .type(q.type().name())
                .options(q.options().stream()
                        .map(o -> TriageResponse.OptionDto.builder()
                                .code(o.code()).label(o.label()).exclusive(o.exclusive()).build())
                        .toList())
                .build();
    }
}
