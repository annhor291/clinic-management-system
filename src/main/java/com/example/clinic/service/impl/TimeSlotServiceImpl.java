package com.example.clinic.service.impl;

import com.example.clinic.dto.response.TimeSlotResponse;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.TimeSlotRepository;
import com.example.clinic.service.TimeSlotService;
import com.example.clinic.mapper.TimeSlotMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final DoctorRepository doctorRepository;
    private final TimeSlotMapper timeSlotMapper;

    // Lấy tất cả slot của bác sĩ trong 1 ngày
    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getByDoctorAndDate(Long doctorId, LocalDate date) {
        validateDoctor(doctorId);
        return timeSlotRepository
                .findByDoctorIdAndSlotDateOrderByStartTimeAsc(doctorId, date)
                .stream()
                .map(timeSlotMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Lấy slot còn trống của bác sĩ trong 1 ngày
    // Đây là chức năng CHECK LỊCH TRỐNG
    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getAvailableByDoctorAndDate(Long doctorId, LocalDate date) {
        validateDoctor(doctorId);
        return timeSlotRepository
                .findByDoctorIdAndSlotDateAndStatusOrderByStartTimeAsc(
                        doctorId, date, SlotStatus.AVAILABLE)
                .stream()
                .map(timeSlotMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Lấy slot còn trống của bác sĩ trong khoảng ngày
    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getAvailableByDoctorAndDateRange(
            Long doctorId, LocalDate startDate, LocalDate endDate) {
        validateDoctor(doctorId);
        return timeSlotRepository
                .findAvailableByDoctorIdAndDateRange(doctorId, startDate, endDate)
                .stream()
                .map(timeSlotMapper::toResponse)
                .collect(Collectors.toList());
    }

    // Lấy chi tiết 1 slot
    @Override
    @Transactional(readOnly = true)
    public TimeSlotResponse getById(Long id) {
        return timeSlotRepository.findById(id)
                .map(timeSlotMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy slot với id: " + id));
    }

    // ===== Private helper =====
    private void validateDoctor(Long doctorId) {
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Không tìm thấy bác sĩ với id: " + doctorId);
        }
    }
}
