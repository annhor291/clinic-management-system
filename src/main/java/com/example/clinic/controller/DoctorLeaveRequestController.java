package com.example.clinic.controller;

import com.example.clinic.dto.request.LeaveRejectRequest;
import com.example.clinic.dto.request.LeaveRequestSubmitRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.LeaveRequestResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.service.DoctorLeaveRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/work-schedule/leave-requests")
@RequiredArgsConstructor
public class DoctorLeaveRequestController {

    private final DoctorLeaveRequestService leaveRequestService;

    // POST /api/v1/work-schedule/leave-requests
    @PostMapping
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> submit(
            @Valid @RequestBody LeaveRequestSubmitRequest request) {
        LeaveRequestResponse response = leaveRequestService.submit(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Nộp đơn xin nghỉ thành công", response));
    }

    // GET /api/v1/work-schedule/leave-requests/me?page=0&size=10
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponse>>> getMyLeaveRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đơn nghỉ thành công",
                leaveRequestService.getMyLeaveRequests(page, size)));
    }

    // GET /api/v1/work-schedule/leave-requests/pending?page=0&size=10
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponse>>> getPending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy danh sách chờ duyệt thành công",
                leaveRequestService.getPending(page, size)));
    }

    // GET /api/v1/work-schedule/leave-requests/doctor/{doctorId}?page=0&size=10
    @GetMapping("/doctor/{doctorId}")
    public ResponseEntity<ApiResponse<PageResponse<LeaveRequestResponse>>> getByDoctor(
            @PathVariable Long doctorId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.success("Lấy lịch sử đơn nghỉ thành công",
                leaveRequestService.getByDoctor(doctorId, page, size)));
    }

    // GET /api/v1/work-schedule/leave-requests/{id}
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin đơn nghỉ thành công",
                leaveRequestService.getById(id)));
    }

    // PUT /api/v1/work-schedule/leave-requests/{id}/approve
    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> approve(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Duyệt đơn nghỉ thành công",
                leaveRequestService.approve(id)));
    }

    // PUT /api/v1/work-schedule/leave-requests/{id}/reject
    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<LeaveRequestResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody LeaveRejectRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Từ chối đơn nghỉ thành công",
                leaveRequestService.reject(id, request)));
    }
}
