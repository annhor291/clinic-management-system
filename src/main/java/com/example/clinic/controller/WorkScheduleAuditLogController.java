package com.example.clinic.controller;

import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.WorkScheduleAuditLogResponse;
import com.example.clinic.service.WorkScheduleAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-schedule/audit-logs")
@RequiredArgsConstructor
public class WorkScheduleAuditLogController {

    private final WorkScheduleAuditLogService auditLogService;

    // GET /api/v1/work-schedule/audit-logs/doctor/{doctorId}?page=0&size=10
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<PageResponse<WorkScheduleAuditLogResponse>>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy log lịch làm việc thành công",
                auditLogService.getByDoctor(doctorId, page, size)));
    }
}
