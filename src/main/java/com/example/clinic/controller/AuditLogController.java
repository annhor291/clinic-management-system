package com.example.clinic.controller;

import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.AuditLogResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.enums.AuditActionType;
import com.example.clinic.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    // GET /api/v1/audit-logs/target/USER/5?page=0&size=10
    @GetMapping("/target/{entityType}/{entityId}")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> getByTarget(
            @PathVariable String entityType,
            @PathVariable Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy audit log thành công",
                auditLogService.getByTarget(entityType, entityId, page, size)));
    }

    // GET /api/v1/audit-logs/search?actionType=USER_ROLE_CHANGED&targetEntityType=USER&page=0&size=10
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) String targetEntityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Tìm kiếm audit log thành công",
                auditLogService.search(actionType, targetEntityType, page, size)));
    }
}
