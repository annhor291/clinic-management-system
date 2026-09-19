package com.example.clinic.service;

import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.WorkScheduleAuditLogResponse;
import com.example.clinic.entity.enums.WorkScheduleActionType;

public interface WorkScheduleAuditLogService {

    // Ghi 1 dòng log — performedBy tự lấy từ SecurityContext (người đang thực hiện thao tác)
    void record(Long doctorId, WorkScheduleActionType actionType, String details,
                String relatedEntityType, Long relatedEntityId);

    PageResponse<WorkScheduleAuditLogResponse> getByDoctor(Long doctorId, int page, int size);
}
