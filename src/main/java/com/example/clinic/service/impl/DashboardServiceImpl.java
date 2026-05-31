package com.example.clinic.service.impl;

import com.example.clinic.dto.response.DashboardResponse;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.projection.AppointmentByDoctorCount;
import com.example.clinic.projection.AppointmentBySpecialtyCount;
import com.example.clinic.projection.AppointmentStatusCount;
import com.example.clinic.projection.MonthlyRevenueStats;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;

    // Lấy toàn bộ thống kê trong khoảng thời gian
    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard(LocalDateTime fromDate, LocalDateTime toDate) {
        return DashboardResponse.builder()
                .overview(buildOverview(fromDate, toDate))
                .appointmentsByStatus(buildAppointmentsByStatus(fromDate, toDate))
                .topDoctors(buildTopDoctors(fromDate, toDate))
                .appointmentsBySpecialty(buildAppointmentsBySpecialty(fromDate, toDate))
                .monthlyRevenue(buildMonthlyRevenue(fromDate, toDate))
                .build();
    }

    // Lấy thống kê theo tháng cụ thể
    // VD: tháng 5/2026 → fromDate = 2026-05-01 00:00, toDate = 2026-05-31 23:59
    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboardByMonth(int month, int year) {
        // YearMonth tự tính ngày đầu tháng và cuối tháng
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime fromDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime toDate = yearMonth.atEndOfMonth().atTime(23, 59, 59);
        return getDashboard(fromDate, toDate);
    }

    // ================================================================
    // Private helpers — mỗi method xây dựng 1 phần của DashboardResponse
    // ================================================================

    // Thống kê tổng quan
    private DashboardResponse.OverviewStats buildOverview(LocalDateTime fromDate, LocalDateTime toDate) {
        // Thời gian hôm nay
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = LocalDate.now().atTime(23, 59, 59);

        // Tính doanh thu tháng từ MonthlyRevenueStats
        List<MonthlyRevenueStats> revenueStats =
                appointmentRepository.getMonthlyRevenueStats(fromDate, toDate);

        BigDecimal revenueMonth = revenueStats.stream()
                .map(MonthlyRevenueStats::getTotalRevenue)
                .filter(r -> r != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return DashboardResponse.OverviewStats.builder()
                .totalPatients(patientRepository.count())
                .totalDoctors(doctorRepository.count())
                .totalAppointmentsToday(
                        appointmentRepository.countByAppointmentTimeBetween(todayStart, todayEnd))
                .totalAppointmentsMonth(
                        appointmentRepository.countByAppointmentTimeBetween(fromDate, toDate))
                .revenueMonth(revenueMonth)
                .build();
    }

    // Thống kê lịch hẹn theo trạng thái
    // Convert List<AppointmentStatusCount> → Map<AppointmentStatus, Long>
    // VD: { PENDING: 10, CONFIRMED: 5, COMPLETED: 20, CANCELLED: 3 }
    private Map<AppointmentStatus, Long> buildAppointmentsByStatus(
            LocalDateTime fromDate, LocalDateTime toDate) {

        List<AppointmentStatusCount> stats =
                appointmentRepository.countByStatusAndDateRange(fromDate, toDate);

        // EnumMap: Map được tối ưu cho key là Enum, nhanh hơn HashMap
        Map<AppointmentStatus, Long> result = new EnumMap<>(AppointmentStatus.class);

        // Khởi tạo tất cả status với giá trị 0 trước
        // Tránh thiếu status nào đó không có trong kết quả query
        for (AppointmentStatus status : AppointmentStatus.values()) {
            result.put(status, 0L);
        }

        // Ghi đè bằng giá trị thực từ query
        stats.forEach(s -> result.put(s.getStatus(), s.getCount()));

        return result;
    }

    // Top bác sĩ có nhiều lịch hẹn nhất (top 10)
    // Convert List<AppointmentByDoctorCount> → List<DoctorStats>
    private List<DashboardResponse.DoctorStats> buildTopDoctors(
            LocalDateTime fromDate, LocalDateTime toDate) {

        // Lấy top 10 bác sĩ nhiều lịch nhất
        List<AppointmentByDoctorCount> stats =
                appointmentRepository.countByDoctorAndDateRange(fromDate, toDate, 10);

        return stats.stream()
                .map(s -> DashboardResponse.DoctorStats.builder()
                        .doctorId(s.getDoctorId())
                        .doctorName(s.getDoctorName())
                        .specialtyName(s.getSpecialtyName())
                        .totalAppointments(s.getTotalAppointments())
                        .build())
                .collect(Collectors.toList());
    }

    // Thống kê theo chuyên khoa
    // Convert List<AppointmentBySpecialtyCount> → List<SpecialtyStats>
    private List<DashboardResponse.SpecialtyStats> buildAppointmentsBySpecialty(
            LocalDateTime fromDate, LocalDateTime toDate) {

        List<AppointmentBySpecialtyCount> stats =
                appointmentRepository.countBySpecialtyAndDateRange(fromDate, toDate);

        return stats.stream()
                .map(s -> DashboardResponse.SpecialtyStats.builder()
                        .specialtyId(s.getSpecialtyId())
                        .specialtyName(s.getSpecialtyName())
                        .totalAppointments(s.getTotalAppointments())
                        .build())
                .collect(Collectors.toList());
    }

    // Doanh thu theo tháng
    // Convert List<MonthlyRevenueStats> → List<MonthlyRevenue>
    private List<DashboardResponse.MonthlyRevenue> buildMonthlyRevenue(
            LocalDateTime fromDate, LocalDateTime toDate) {

        List<MonthlyRevenueStats> stats =
                appointmentRepository.getMonthlyRevenueStats(fromDate, toDate);

        return stats.stream()
                .map(s -> DashboardResponse.MonthlyRevenue.builder()
                        .month(s.getMonth())
                        .year(s.getYear())
                        .totalAppointments(s.getTotalAppointments())
                        // Nếu doanh thu null (chưa có lịch hoàn thành) thì trả về 0
                        .totalRevenue(s.getTotalRevenue() != null
                                ? s.getTotalRevenue()
                                : BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());
    }
}
