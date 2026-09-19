package com.example.clinic.service.impl;

import com.example.clinic.dto.request.DayShiftRequest;
import com.example.clinic.dto.request.RegistrationRejectRequest;
import com.example.clinic.dto.request.WeeklyRegistrationRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.WeeklyRegistrationResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.RegistrationStatus;
import com.example.clinic.entity.enums.ShiftType;
import com.example.clinic.entity.enums.WorkScheduleActionType;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.mapper.DoctorWeeklyRegistrationMapper;
import com.example.clinic.repository.DoctorLeaveRequestRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.DoctorWeeklyRegistrationRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.DoctorScheduleService;
import com.example.clinic.service.DoctorWeeklyRegistrationService;
import com.example.clinic.service.WorkScheduleAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DoctorWeeklyRegistrationServiceImpl implements DoctorWeeklyRegistrationService {

    private static final List<DayOfWeek> MANDATORY_DAYS = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    );

    private static final LocalTime MORNING_START = LocalTime.of(8, 0);
    private static final LocalTime MORNING_END = LocalTime.of(12, 0);
    private static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_END = LocalTime.of(17, 0);

    private final DoctorWeeklyRegistrationRepository registrationRepository;
    private final DoctorLeaveRequestRepository leaveRequestRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final DoctorWeeklyRegistrationMapper mapper;
    private final DoctorScheduleService doctorScheduleService;
    private final WorkScheduleAuditLogService auditLogService;

    @Override
    @Transactional
    public WeeklyRegistrationResponse submit(WeeklyRegistrationRequest request) {
        Doctor doctor = getCurrentDoctorOrThrow();

        if (request.getWeekStartDate().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalStateException("Ngày bắt đầu tuần phải là Thứ Hai");
        }

        // Không cho đăng ký cho tuần đã trôi qua — so với Thứ Hai của tuần hiện tại
        LocalDate currentWeekMonday = LocalDate.now()
                .minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        if (request.getWeekStartDate().isBefore(currentWeekMonday)) {
            throw new IllegalStateException("Không thể đăng ký lịch làm việc cho tuần đã qua");
        }

        if (registrationRepository.existsByDoctorIdAndWeekStartDate(doctor.getId(), request.getWeekStartDate())) {
            throw new DuplicateResourceException("Bạn đã đăng ký lịch cho tuần bắt đầu " + request.getWeekStartDate());
        }

        validateDays(request.getDays());

        DoctorWeeklyRegistration registration = DoctorWeeklyRegistration.builder()
                .doctor(doctor)
                .weekStartDate(request.getWeekStartDate())
                .status(RegistrationStatus.PENDING)
                .build();

        List<DoctorWeeklyRegistrationDay> days = request.getDays().stream()
                .map(d -> DoctorWeeklyRegistrationDay.builder()
                        .registration(registration)
                        .dayOfWeek(d.getDayOfWeek())
                        .shiftType(d.getShiftType())
                        .build())
                .toList();
        registration.setDays(days);

        DoctorWeeklyRegistration saved = registrationRepository.save(registration);

        List<String> warnings = buildOverlapWarnings(doctor.getId(), request);

        WeeklyRegistrationResponse response = mapper.toResponse(saved);
        response.setWarnings(warnings);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public WeeklyRegistrationResponse getById(Long id) {
        DoctorWeeklyRegistration registration = findByIdOrThrow(id);
        validateViewPermission(registration);
        return mapper.toResponse(registration);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WeeklyRegistrationResponse> getMyRegistrations(int page, int size) {
        Doctor doctor = getCurrentDoctorOrThrow();
        Pageable pageable = PageRequest.of(page, size);
        Page<WeeklyRegistrationResponse> result = registrationRepository
                .findByDoctorIdOrderByWeekStartDateDesc(doctor.getId(), pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WeeklyRegistrationResponse> getByDoctor(Long doctorId, int page, int size) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Bạn không có quyền xem đăng ký lịch của bác sĩ khác");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<WeeklyRegistrationResponse> result = registrationRepository
                .findByDoctorIdOrderByWeekStartDateDesc(doctorId, pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WeeklyRegistrationResponse> getPending(int page, int size) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Bạn không có quyền xem hàng đợi duyệt đăng ký");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<WeeklyRegistrationResponse> result = registrationRepository
                .findByStatusOrderBySubmittedAtAsc(RegistrationStatus.PENDING, pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    @Override
    @Transactional
    public WeeklyRegistrationResponse approve(Long id) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Chỉ lễ tân hoặc admin mới có thể duyệt đăng ký lịch làm việc");
        }

        DoctorWeeklyRegistration registration = findByIdOrThrow(id);

        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể duyệt đăng ký đang ở trạng thái PENDING");
        }

        User currentUser = getCurrentUserOrThrow();

        int generatedCount = generateActualSchedule(registration);

        registration.setStatus(RegistrationStatus.APPROVED);
        registration.setDecidedBy(currentUser);
        registration.setDecidedAt(LocalDateTime.now());

        DoctorWeeklyRegistration saved = registrationRepository.save(registration);

        auditLogService.record(
                registration.getDoctor().getId(),
                WorkScheduleActionType.REGISTRATION_APPROVED,
                "Duyệt đăng ký tuần " + registration.getWeekStartDate() + ", sinh " + generatedCount + " ca làm việc",
                "REGISTRATION",
                registration.getId()
        );

        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public WeeklyRegistrationResponse reject(Long id, RegistrationRejectRequest request) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Chỉ lễ tân hoặc admin mới có thể từ chối đăng ký lịch làm việc");
        }

        DoctorWeeklyRegistration registration = findByIdOrThrow(id);

        if (registration.getStatus() != RegistrationStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể từ chối đăng ký đang ở trạng thái PENDING");
        }

        User currentUser = getCurrentUserOrThrow();

        registration.setStatus(RegistrationStatus.REJECTED);
        registration.setDecidedBy(currentUser);
        registration.setDecidedAt(LocalDateTime.now());
        registration.setRejectionReason(request.getReason());

        DoctorWeeklyRegistration saved = registrationRepository.save(registration);

        auditLogService.record(
                registration.getDoctor().getId(),
                WorkScheduleActionType.REGISTRATION_REJECTED,
                "Từ chối đăng ký tuần " + registration.getWeekStartDate() + ": " + request.getReason(),
                "REGISTRATION",
                registration.getId()
        );

        return mapper.toResponse(saved);
    }

    // ===== Private helpers =====

    // Validate: T2-T6 bắt buộc có mặt đúng 1 lần, shift phải là MORNING/AFTERNOON/FULL.
    // T7/CN tuỳ chọn (không có trong danh sách nghĩa là không làm) — nếu có mặt cũng phải là
    // MORNING/AFTERNOON/FULL, không chấp nhận NONE (không muốn làm thì đừng gửi lên) hay CUSTOM
    // (CUSTOM chỉ dành cho ca tạo thủ công qua API cũ, không áp dụng ở luồng đăng ký này).
    private void validateDays(List<DayShiftRequest> days) {
        Map<DayOfWeek, ShiftType> byDay = new EnumMap<>(DayOfWeek.class);

        for (DayShiftRequest d : days) {
            if (byDay.containsKey(d.getDayOfWeek())) {
                throw new IllegalStateException("Mỗi ngày trong tuần chỉ được khai báo 1 lần: " + d.getDayOfWeek());
            }
            if (d.getShiftType() == ShiftType.NONE || d.getShiftType() == ShiftType.CUSTOM) {
                throw new IllegalStateException(
                        "Ca làm việc không hợp lệ cho " + d.getDayOfWeek() +
                                " — nếu không làm ngày đó, đừng gửi lên; nếu có làm, chọn MORNING/AFTERNOON/FULL");
            }
            byDay.put(d.getDayOfWeek(), d.getShiftType());
        }

        for (DayOfWeek mandatory : MANDATORY_DAYS) {
            if (!byDay.containsKey(mandatory)) {
                throw new IllegalStateException("Phải đăng ký ca làm việc cho " + mandatory + " (Thứ Hai đến Thứ Sáu là bắt buộc)");
            }
        }
    }

    // So khớp từng ngày cụ thể trong tuần đăng ký với các đơn nghỉ ĐÃ DUYỆT — trả về cảnh báo mềm,
    // KHÔNG chặn việc submit, chỉ để bác sĩ biết trước khi admin duyệt
    private List<String> buildOverlapWarnings(Long doctorId, WeeklyRegistrationRequest request) {
        LocalDate weekStart = request.getWeekStartDate();
        LocalDate weekEnd = weekStart.plusDays(6);

        List<DoctorLeaveRequest> approvedLeaves =
                leaveRequestRepository.findApprovedOverlapping(doctorId, weekStart, weekEnd);

        if (approvedLeaves.isEmpty()) {
            return List.of();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy");
        List<String> warnings = new ArrayList<>();

        for (DayShiftRequest d : request.getDays()) {
            LocalDate concreteDate = weekStart.plusDays(d.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());

            for (DoctorLeaveRequest leave : approvedLeaves) {
                if (!concreteDate.isBefore(leave.getStartDate()) && !concreteDate.isAfter(leave.getEndDate())) {
                    warnings.add(concreteDate.format(formatter) +
                            " đã có lịch nghỉ được duyệt trước đó (" + leave.getType() + ": " + leave.getReason() + ")");
                }
            }
        }

        return warnings;
    }

    private Doctor getCurrentDoctorOrThrow() {
        return doctorRepository.findByUserId(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa có hồ sơ bác sĩ"));
    }

    private User getCurrentUserOrThrow() {
        return userRepository.findById(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản hiện tại"));
    }

    private DoctorWeeklyRegistration findByIdOrThrow(Long id) {
        return registrationRepository.findByIdWithDays(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đăng ký lịch làm việc với id: " + id));
    }

    private void validateViewPermission(DoctorWeeklyRegistration registration) {
        if (SecurityUtil.isAdminOrReceptionist()) return;

        if (!registration.getDoctor().getUser().getId().equals(SecurityUtil.getCurrentUserId())) {
            throw new AccessDeniedException("Bạn không có quyền xem đăng ký lịch này");
        }
    }

    // Duyệt tuần → chuyển từng DoctorWeeklyRegistrationDay thành DoctorSchedule + TimeSlot thực tế.
    // FULL tách thành 2 ca riêng (sáng + chiều) để tránh sinh nhầm slot vào giờ nghỉ trưa.
    // Trả về số DoctorSchedule đã sinh — dùng để ghi chi tiết vào WorkScheduleAuditLog
    private int generateActualSchedule(DoctorWeeklyRegistration registration) {
        Long doctorId = registration.getDoctor().getId();
        LocalDate weekStart = registration.getWeekStartDate();
        int count = 0;

        for (DoctorWeeklyRegistrationDay day : registration.getDays()) {
            LocalDate concreteDate = weekStart.plusDays(day.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
            ShiftType shift = day.getShiftType();

            if (shift == ShiftType.MORNING) {
                doctorScheduleService.createFromApprovedRegistration(
                        doctorId, concreteDate, MORNING_START, MORNING_END, ShiftType.MORNING);
                count++;

            } else if (shift == ShiftType.AFTERNOON) {
                doctorScheduleService.createFromApprovedRegistration(
                        doctorId, concreteDate, AFTERNOON_START, AFTERNOON_END, ShiftType.AFTERNOON);
                count++;

            } else if (shift == ShiftType.FULL) {
                doctorScheduleService.createFromApprovedRegistration(
                        doctorId, concreteDate, MORNING_START, MORNING_END, ShiftType.MORNING);
                doctorScheduleService.createFromApprovedRegistration(
                        doctorId, concreteDate, AFTERNOON_START, AFTERNOON_END, ShiftType.AFTERNOON);
                count += 2;
            }
        }
        return count;
    }
}
