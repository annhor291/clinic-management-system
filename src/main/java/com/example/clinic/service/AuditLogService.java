package com.example.clinic.service;

import com.example.clinic.dto.response.AuditLogResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.enums.AuditActionType;

public interface AuditLogService {

    // before/after: truyền Object bất kỳ hoặc null — tự serialize sang JSON bên trong.
    // Chỉ truyền khác null khi hành động đó thực sự cần biết giá trị cụ thể trước/sau.
    void record(AuditActionType actionType, String targetEntityType, Long targetEntityId,
                String description, Object before, Object after);

    PageResponse<AuditLogResponse> getByTarget(String targetEntityType, Long targetEntityId,
                                               int page, int size);

    PageResponse<AuditLogResponse> search(AuditActionType actionType, String targetEntityType,
                                          int page, int size);
}
