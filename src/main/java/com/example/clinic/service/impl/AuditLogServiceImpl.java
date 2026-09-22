package com.example.clinic.service.impl;

import com.example.clinic.dto.response.AuditLogResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.AuditLog;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AuditActionType;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.mapper.AuditLogMapper;
import com.example.clinic.repository.AuditLogRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogServiceImpl  implements AuditLogService {

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final AuditLogMapper mapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void record(AuditActionType actionType, String targetEntityType, Long targetEntityId,
                       String description, Object before, Object after) {
        User actor = userRepository.findById(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản hiện tại"));

        String safeDescription = description != null && description.length() > MAX_DESCRIPTION_LENGTH
                ? description.substring(0, MAX_DESCRIPTION_LENGTH)
                : description;

        AuditLog log = AuditLog.builder()
                .user(actor)
                .action(actionType)
                .entityType(targetEntityType)
                .entityId(targetEntityId)
                .description(safeDescription)
                .oldValue(toJsonOrNull(before))
                .newValue(toJsonOrNull(after))
                .build();

        auditLogRepository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> getByTarget(String targetEntityType, Long targetEntityId, int page, int size) {
        requireAdmin();
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogResponse> result = auditLogRepository
                .findByEntityTypeAndEntityIdOrderByCreatedAtDesc(targetEntityType, targetEntityId, pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditActionType actionType, String targetEntityType, int page, int size) {
        requireAdmin();
        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLogResponse> result = auditLogRepository
                .search(actionType, targetEntityType, pageable)
                .map(mapper::toResponse);
        return PageResponse.of(result);
    }

    // ===== Private helpers =====

    private String toJsonOrNull(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.error("Không thể serialize audit snapshot: {}", e.getMessage());
            return null;
        }
    }

    private void requireAdmin() {
        if (!SecurityUtil.isAdmin()) {
            throw new AccessDeniedException("Chỉ admin mới có thể xem Audit Log");
        }
    }

}
