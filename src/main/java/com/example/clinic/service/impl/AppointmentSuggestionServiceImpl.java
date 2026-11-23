package com.example.clinic.service.impl;

import com.example.clinic.booking.availability.DoctorAvailabilityGateway;
import com.example.clinic.booking.availability.PatientAvailabilityGateway;
import com.example.clinic.booking.availability.PatientScheduleCheckResult;
import com.example.clinic.booking.config.BookingProperties;
import com.example.clinic.booking.engine.BookingContext;
import com.example.clinic.booking.engine.BookingRuleEngine;
import com.example.clinic.booking.engine.BookingRuleViolationException;
import com.example.clinic.booking.suggestion.SlotSuggestionScorer;
import com.example.clinic.dto.response.SuggestedSlotResponse;
import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.TimeSlot;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.TimeSlotRepository;
import com.example.clinic.service.AppointmentService;
import com.example.clinic.service.AppointmentSuggestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentSuggestionServiceImpl implements AppointmentSuggestionService {

    // Giới hạn số slot ứng viên đọc từ DB (1 phòng khám, 14 ngày: dư sức)
    private static final int MAX_CANDIDATES = 2000;

    private final AppointmentService appointmentService;
    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final DoctorAvailabilityGateway doctorAvailabilityGateway;
    private final PatientAvailabilityGateway patientAvailabilityGateway;
    private final BookingRuleEngine bookingRuleEngine;
    private final BookingProperties bookingProperties;
    private final SlotSuggestionScorer scorer;

    @Override
    @Transactional(readOnly = true)
    public List<SuggestedSlotResponse> suggestAlternatives(Long cancelledAppointmentId) {
        // Kiểm tra quyền xem lịch hẹn (ném AccessDeniedException / ResourceNotFoundException)
        appointmentService.getById(cancelledAppointmentId);

        Appointment origin = appointmentRepository.findById(cancelledAppointmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch hẹn với id: " + cancelledAppointmentId));

        if (origin.getStatus() != AppointmentStatus.CANCELLED || !origin.isCancelledByClinic()) {
            throw new IllegalStateException(
                    "Chỉ gợi ý đặt lịch tương tự khi lịch hẹn bị phòng khám hủy");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDate originDate = origin.getTimeSlot().getSlotDate();
        LocalDate baseDate = originDate.isAfter(today) ? originDate : today;
        LocalDate toDate = baseDate.plusDays(bookingProperties.getSuggestionWindowDays());

        List<TimeSlot> candidates = timeSlotRepository.findAvailableBySpecialtyAndDateRange(
                origin.getDoctor().getSpecialty().getId(),
                SlotStatus.AVAILABLE, today, toDate, PageRequest.of(0, MAX_CANDIDATES));

        List<SlotSuggestionScorer.Scored> ranked = candidates.stream()
                .map(slot -> scorer.score(slot, origin, baseDate))
                .sorted(scorer.ranking())
                .toList();

        // Duyệt theo điểm, chỉ nhận slot qua được toàn bộ Booking Rule cho CHÍNH bệnh nhân này
        Long patientId = origin.getPatient().getId();
        List<SuggestedSlotResponse> result = new ArrayList<>();
        for (SlotSuggestionScorer.Scored scored : ranked) {
            if (result.size() >= bookingProperties.getSuggestionMaxResults()) break;
            if (passesBookingRules(scored.slot(), patientId, now)) {
                result.add(toResponse(scored));
            }
        }
        return result;
    }

    private boolean passesBookingRules(TimeSlot slot, Long patientId, LocalDateTime now) {
        boolean hasConflict = doctorAvailabilityGateway.hasConflictingAppointment(slot.getId());
        PatientScheduleCheckResult check = patientAvailabilityGateway.check(
                patientId, slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), null);

        BookingContext context = new BookingContext(
                slot.getDoctor().getId(), patientId,
                slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(),
                null, now, slot.getStatus(),
                hasConflict, check.hasOverlap(), check.hasBufferViolation());
        try {
            bookingRuleEngine.validate(context);
            return true;
        } catch (BookingRuleViolationException e) {
            return false;
        }
    }

    private SuggestedSlotResponse toResponse(SlotSuggestionScorer.Scored scored) {
        TimeSlot slot = scored.slot();
        Doctor doctor = slot.getDoctor();
        return SuggestedSlotResponse.builder()
                .timeSlotId(slot.getId())
                .doctorId(doctor.getId())
                .doctorName(doctor.getFullName())
                .doctorTitle(doctor.getTitle())
                .specialtyName(doctor.getSpecialty().getName())
                .consultationFee(doctor.getConsultationFee())
                .slotDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .score(scored.score())
                .reasons(scored.reasons())
                .build();
    }
}
