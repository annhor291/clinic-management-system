package com.example.clinic.service.impl;

import com.example.clinic.dto.request.LeaveRejectRequest;
import com.example.clinic.dto.request.LeaveRequestSubmitRequest;
import com.example.clinic.dto.response.LeaveRequestResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.*;
import com.example.clinic.entity.enums.*;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.mapper.DoctorLeaveRequestMapper;
import com.example.clinic.repository.*;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.AppointmentService;
import com.example.clinic.service.DoctorLeaveRequestService;
import com.example.clinic.service.DoctorScheduleService;
import com.example.clinic.service.WorkScheduleAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DoctorLeaveRequestServiceImpl implements DoctorLeaveRequestService {

    private final DoctorLeaveRequestRepository leaveRequestRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final DoctorLeaveRequestMapper mapper;
    private final TimeSlotRepository timeSlotRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final DoctorScheduleService doctorScheduleService;
    private final WorkScheduleAuditLogService auditLogService;

    @Override
    @Transactional
    public LeaveRequestResponse submit(LeaveRequestSubmitRequest request) {
        Doctor doctor = getCurrentDoctorOrThrow();

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalStateException("Ngày kết thúc nghỉ không thể trước ngày bắt đầu");
        }

        if (leaveRequestRepository.existsOverlappingActiveRequest(
                doctor.getId(), request.getStartDate(), request.getEndDate())) {
            throw new DuplicateResourceException(
                    "Bạn đã có đơn nghỉ khác (đang chờ duyệt hoặc đã duyệt) chồng lấn khoảng thời gian này");
        }

        DoctorLeaveRequest leaveRequest = DoctorLeaveRequest.builder()
                .doctor(doctor)
                .type(request.getType())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .reason(request.getReason())
                .status(LeaveStatus.PENDING)
                .build();

        DoctorLeaveRequest saved = leaveRequestRepository.save(leaveRequest);

        List<String> warnings = buildScheduleConflictWarnings(doctor.getId(), request.getStartDate(), request.getEndDate());

        LeaveRequestResponse response = mapper.toResponse(saved);
        response.setWarnings(warnings);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveRequestResponse getById(Long id) {
        DoctorLeaveRequest leaveRequest = findByIdOrThrow(id);
        validateViewPermission(leaveRequest);
        return mapper.toResponse(leaveRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> getMyLeaveRequests(int page, int size) {
        Doctor doctor = getCurrentDoctorOrThrow();
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequestResponse> result = leaveRequestRepository
                .findByDoctorIdOrderByStartDateDesc(doctor.getId(), pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> getByDoctor(Long doctorId, int page, int size) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Bạn không có quyền xem đơn nghỉ của bác sĩ khác");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequestResponse> result = leaveRequestRepository
                .findByDoctorIdOrderByStartDateDesc(doctorId, pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LeaveRequestResponse> getPending(int page, int size) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Bạn không có quyền xem hàng đợi duyệt đơn nghỉ");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveRequestResponse> result = leaveRequestRepository
                .findByStatusOrderBySubmittedAtAsc(LeaveStatus.PENDING, pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    @Override
    @Transactional
    public LeaveRequestResponse approve(Long id) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Chỉ lễ tân hoặc admin mới có thể duyệt đơn nghỉ");
        }

        DoctorLeaveRequest leaveRequest = findByIdOrThrow(id);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể duyệt đơn nghỉ đang ở trạng thái PENDING");
        }

        User currentUser = getCurrentUserOrThrow();

        int cancelledCount = applyLeaveToActualSchedule(leaveRequest);

        leaveRequest.setStatus(LeaveStatus.APPROVED);
        leaveRequest.setDecidedBy(currentUser);
        leaveRequest.setDecidedAt(LocalDateTime.now());

        DoctorLeaveRequest saved = leaveRequestRepository.save(leaveRequest);


        auditLogService.record(
                leaveRequest.getDoctor().getId(),
                WorkScheduleActionType.LEAVE_APPROVED,
                "Duyệt " + (leaveRequest.getType() == LeaveType.PLANNED ? "nghỉ phép" : "nghỉ đột xuất") +
                        " từ " + leaveRequest.getStartDate() + " đến " + leaveRequest.getEndDate() +
                        ", tự động hủy " + cancelledCount + " lịch hẹn",
                "LEAVE_REQUEST",
                leaveRequest.getId()
        );


        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public LeaveRequestResponse reject(Long id, LeaveRejectRequest request) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Chỉ lễ tân hoặc admin mới có thể từ chối đơn nghỉ");
        }

        DoctorLeaveRequest leaveRequest = findByIdOrThrow(id);

        if (leaveRequest.getStatus() != LeaveStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể từ chối đơn nghỉ đang ở trạng thái PENDING");
        }

        User currentUser = getCurrentUserOrThrow();

        leaveRequest.setStatus(LeaveStatus.REJECTED);
        leaveRequest.setDecidedBy(currentUser);
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequest.setRejectionReason(request.getReason());

        DoctorLeaveRequest saved = leaveRequestRepository.save(leaveRequest);

        auditLogService.record(
                leaveRequest.getDoctor().getId(),
                WorkScheduleActionType.LEAVE_REJECTED,
                "Từ chối đơn " + (leaveRequest.getType() == LeaveType.PLANNED ? "nghỉ phép" : "nghỉ đột xuất") +
                        " từ " + leaveRequest.getStartDate() + " đến " + leaveRequest.getEndDate() +
                        ": " + request.getReason(),
                "LEAVE_REQUEST",
                leaveRequest.getId()
        );

        return mapper.toResponse(saved);
    }

    // ===== Private helpers =====

    // Kiểm tra bác sĩ có lịch làm việc (DoctorSchedule active) nào trong khoảng nghỉ không —
    // CẢNH BÁO MỀM, không chặn submit, chỉ để bác sĩ biết trước khi admin duyệt
    private List<String> buildScheduleConflictWarnings(Long doctorId, java.time.LocalDate startDate, java.time.LocalDate endDate) {
        List<DoctorSchedule> existingSchedules =
                scheduleRepository.findByDoctorIdAndDateRange(doctorId, startDate, endDate);

        if (existingSchedules.isEmpty()) {
            return List.of();
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy");
        return existingSchedules.stream()
                .map(s -> "Bạn đã có lịch làm việc ca " + s.getShiftType() +
                        " vào " + s.getWorkDate().format(formatter))
                .toList();
    }

    private Doctor getCurrentDoctorOrThrow() {
        return doctorRepository.findByUserId(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa có hồ sơ bác sĩ"));
    }

    private User getCurrentUserOrThrow() {
        return userRepository.findById(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản hiện tại"));
    }

    private DoctorLeaveRequest findByIdOrThrow(Long id) {
        return leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy đơn nghỉ với id: " + id));
    }

    private void validateViewPermission(DoctorLeaveRequest leaveRequest) {
        if (SecurityUtil.isAdminOrReceptionist()) return;

        if (!leaveRequest.getDoctor().getUser().getId().equals(SecurityUtil.getCurrentUserId())) {
            throw new AccessDeniedException("Bạn không có quyền xem đơn nghỉ này");
        }
    }

    private int applyLeaveToActualSchedule(DoctorLeaveRequest leaveRequest) {
        Long doctorId = leaveRequest.getDoctor().getId();
        List<DoctorSchedule> schedules = scheduleRepository.findByDoctorIdAndDateRange(
                doctorId, leaveRequest.getStartDate(), leaveRequest.getEndDate());

        String cancelReason = buildCancelReason(leaveRequest);
        int cancelledCount = 0;

        for (DoctorSchedule schedule : schedules) {
            List<TimeSlot> slots = timeSlotRepository.findByScheduleId(schedule.getId());

            for (TimeSlot slot : slots) {
                if (slot.getStatus() == SlotStatus.BOOKED) {
                    Optional<Appointment> appt = appointmentRepository.findByTimeSlotIdAndStatusIn(
                            slot.getId(), List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED));
                    if (appt.isPresent()) {
                        appointmentService.cancelDueToDoctorLeave(appt.get().getId(), cancelReason);
                        cancelledCount++;
                    }
                }
            }

            doctorScheduleService.deactivate(schedule.getId());
        }
        return cancelledCount;
    }

    private String buildCancelReason(DoctorLeaveRequest leaveRequest) {
        String typeLabel = leaveRequest.getType() == LeaveType.PLANNED ? "nghỉ phép" : "nghỉ đột xuất";
        return "Bác sĩ " + typeLabel + ": " + leaveRequest.getReason();
    }


}
