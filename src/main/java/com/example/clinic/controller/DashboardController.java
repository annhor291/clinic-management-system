package com.example.clinic.controller;

import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.DashboardResponse;
import com.example.clinic.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    // GET /api/v1/dashboard?fromDate=2026-05-01T00:00:00&toDate=2026-05-31T23:59:59
    // Lấy thống kê trong khoảng thời gian bất kỳ
    @GetMapping
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime toDate) {

        // Validate fromDate phải trước toDate
        if (fromDate.isAfter(toDate)) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Ngày bắt đầu phải trước ngày kết thúc"));
        }

        DashboardResponse dashboard = dashboardService.getDashboard(fromDate, toDate);
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê thành công", dashboard));
    }

    // GET /api/v1/dashboard/monthly?month=5&year=2026
    // Lấy thống kê theo tháng cụ thể — tiện hơn khi frontend chỉ cần truyền tháng/năm
    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboardByMonth(
            @RequestParam int month,
            @RequestParam int year) {

        // Validate tháng hợp lệ
        if (month < 1 || month > 12) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Tháng không hợp lệ (1-12)"));
        }

        DashboardResponse dashboard = dashboardService.getDashboardByMonth(month, year);
        return ResponseEntity.ok(ApiResponse.success("Lấy thống kê tháng thành công", dashboard));
    }
}
