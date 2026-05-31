package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.AppointmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    // ===== Tổng quan =====
    private OverviewStats overview;

    // ===== Thống kê lịch hẹn theo trạng thái =====
    private Map<AppointmentStatus, Long> appointmentsByStatus;

    // ===== Top bác sĩ có nhiều lịch hẹn nhất =====
    private List<DoctorStats> topDoctors;

    // ===== Thống kê theo chuyên khoa =====
    private List<SpecialtyStats> appointmentsBySpecialty;

    // ===== Doanh thu theo tháng =====
    private List<MonthlyRevenue> monthlyRevenue;


    // Thống kê tổng quan
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OverviewStats {
        private long totalPatients;          // Tổng số bệnh nhân
        private long totalDoctors;           // Tổng số bác sĩ
        private long totalAppointmentsToday; // Tổng lịch hẹn hôm nay
        private long totalAppointmentsMonth; // Tổng lịch hẹn trong tháng
        private BigDecimal revenueMonth;     // Doanh thu trong tháng
    }

    // Thống kê theo bác sĩ
    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DoctorStats {
        private Long doctorId;
        private String doctorName;
        private String specialtyName;
        private Long totalAppointments;
    }

    // Thống kê theo chuyên khoa
    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SpecialtyStats {
        private Long specialtyId;
        private String specialtyName;
        private Long totalAppointments;
    }

    // Doanh thu theo tháng
    @Getter @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyRevenue {
        private Integer month;
        private Integer year;
        private Long totalAppointments;
        private BigDecimal totalRevenue;
    }
}
