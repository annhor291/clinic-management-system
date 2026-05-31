package com.example.clinic.service;

import com.example.clinic.dto.response.DashboardResponse;

import java.time.LocalDateTime;

public interface DashboardService {

    // Lấy toàn bộ thống kê cho Admin dashboard
    // fromDate, toDate: khoảng thời gian thống kê
    DashboardResponse getDashboard(LocalDateTime fromDate, LocalDateTime toDate);

    // Lấy thống kê theo tháng cụ thể
    // VD: tháng 5 năm 2026
    DashboardResponse getDashboardByMonth(int month, int year);
}
