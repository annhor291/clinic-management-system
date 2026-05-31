package com.example.clinic.projection;

import java.math.BigDecimal;

public interface MonthlyRevenueStats {

    // Tháng (1 - 12)
    Integer getMonth();

    // Năm (VD: 2026)
    Integer getYear();

    // Tổng số lịch hẹn đã hoàn thành trong tháng
    Long getTotalAppointments();

    // Tổng doanh thu trong tháng
    BigDecimal getTotalRevenue();
}
