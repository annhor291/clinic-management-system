package com.example.clinic.service.impl;

import com.example.clinic.entity.DoctorSchedule;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.DoctorScheduleRepository;
import com.example.clinic.service.AppointmentService;
import com.example.clinic.service.DoctorBlockService;
import com.example.clinic.service.DoctorScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorBlockServiceImpl implements DoctorBlockService {

    private static final LocalDate FAR_FUTURE = LocalDate.of(2999, 12, 31);

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final DoctorScheduleRepository scheduleRepository;
    private final DoctorScheduleService doctorScheduleService;

    @Override
    @Transactional
    public int blockDoctor(Long doctorId, LocalDate from, LocalDate to, String reason) {
        LocalDate today = LocalDate.now();
        LocalDate start = from.isBefore(today) ? today : from;
        LocalDate end = to != null ? to : FAR_FUTURE;

        // Chỉ hủy lịch còn ở tương lai, không đụng vào lịch đã qua giờ hẹn
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime fromTime = start.atStartOfDay().isAfter(now) ? start.atStartOfDay() : now;
        List<Long> ids = appointmentRepository.findActiveIdsByDoctorBetween(
                doctorId, fromTime, end.plusDays(1).atStartOfDay());
        ids.forEach(id -> appointmentService.cancelByClinic(id, reason));

        // Slot đã được khóa khi hủy lịch; deactivate khóa nốt các slot còn trống của ca
        for (DoctorSchedule schedule : scheduleRepository.findByDoctorIdAndDateRange(doctorId, start, end)) {
            if (schedule.isActive()) {
                doctorScheduleService.blockSchedule(schedule.getId());
            }
        }
        return ids.size();
    }
}
