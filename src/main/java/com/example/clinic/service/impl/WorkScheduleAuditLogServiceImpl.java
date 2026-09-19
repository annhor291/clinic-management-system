package com.example.clinic.service.impl;

import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.WorkScheduleAuditLogResponse;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.User;
import com.example.clinic.entity.WorkScheduleAuditLog;
import com.example.clinic.entity.enums.WorkScheduleActionType;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.mapper.WorkScheduleAuditLogMapper;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.WorkScheduleAuditLogRepository;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.WorkScheduleAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkScheduleAuditLogServiceImpl implements WorkScheduleAuditLogService {

    private final WorkScheduleAuditLogRepository logRepository;
    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final WorkScheduleAuditLogMapper mapper;

    @Override
    @Transactional
    public void record(Long doctorId, WorkScheduleActionType actionType, String details,
                       String relatedEntityType, Long relatedEntityId) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bác sĩ với id: " + doctorId));

        User performedBy = userRepository.findById(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản hiện tại"));

        WorkScheduleAuditLog log = WorkScheduleAuditLog.builder()
                .doctor(doctor)
                .actionType(actionType)
                .performedBy(performedBy)
                .details(details)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(relatedEntityId)
                .build();

        logRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<WorkScheduleAuditLogResponse> getByDoctor(Long doctorId, int page, int size) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Bạn không có quyền xem log lịch làm việc");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<WorkScheduleAuditLogResponse> result = logRepository
                .findByDoctorIdOrderByPerformedAtDesc(doctorId, pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }
}
