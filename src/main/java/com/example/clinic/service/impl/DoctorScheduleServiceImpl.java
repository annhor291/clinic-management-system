package com.example.clinic.service.impl;

import com.example.clinic.dto.request.DoctorScheduleRequest;
import com.example.clinic.dto.response.DoctorScheduleResponse;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.DoctorSchedule;
import com.example.clinic.entity.TimeSlot;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.DoctorScheduleRepository;
import com.example.clinic.repository.TimeSlotRepository;
import com.example.clinic.service.DoctorScheduleService;
import com.example.clinic.mapper.DoctorScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorScheduleServiceImpl implements DoctorScheduleService {

    private final DoctorScheduleRepository scheduleRepository;
    private final DoctorRepository doctorRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final DoctorScheduleMapper scheduleMapper;

    // Tạo ca làm việc và tự động sinh các TimeSlot
    @Override
    @Transactional
    public DoctorScheduleResponse create(DoctorScheduleRequest request) {
        // Kiểm tra bác sĩ tồn tại không
        Doctor doctor = doctorRepository.findById(request.getDoctorId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bác sĩ với id: " + request.getDoctorId()));

        // Kiểm tra bác sĩ đã có ca làm việc trong ngày đó chưa
        if (scheduleRepository.existsByDoctorIdAndWorkDate(
                request.getDoctorId(), request.getWorkDate())) {
            throw new DuplicateResourceException(
                    "Bác sĩ đã có ca làm việc vào ngày " + request.getWorkDate());
        }

        // Validate giờ bắt đầu phải trước giờ kết thúc
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new IllegalStateException("Giờ bắt đầu phải trước giờ kết thúc");
        }

        // Tạo schedule
        DoctorSchedule schedule = scheduleMapper.toEntity(request);
        schedule.setDoctor(doctor);
        DoctorSchedule savedSchedule = scheduleRepository.save(schedule);

        // Tự động sinh TimeSlot từ schedule
        List<TimeSlot> slots = generateTimeSlots(savedSchedule);
        timeSlotRepository.saveAll(slots);

        // Load lại schedule với slots để trả về
        savedSchedule.setTimeSlots(slots);
        return scheduleMapper.toResponseWithSlots(savedSchedule);
    }

    // Lấy chi tiết ca làm việc kèm slot
    @Override
    @Transactional(readOnly = true)
    public DoctorScheduleResponse getById(Long id) {
        DoctorSchedule schedule = findByIdOrThrow(id);
        return scheduleMapper.toResponseWithSlots(schedule);
    }

    // Lấy lịch làm việc của bác sĩ trong khoảng ngày
    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> getByDoctorAndDateRange(
            Long doctorId, LocalDate startDate, LocalDate endDate) {

        // Kiểm tra bác sĩ tồn tại
        if (!doctorRepository.existsById(doctorId)) {
            throw new ResourceNotFoundException("Không tìm thấy bác sĩ với id: " + doctorId);
        }

        return scheduleRepository.findByDoctorIdAndDateRange(doctorId, startDate, endDate)
                .stream()
                .map(scheduleMapper::toResponseWithSlots)
                .collect(Collectors.toList());
    }

    // Lấy lịch theo tuần (7 ngày từ weekStart)
    @Override
    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> getWeeklySchedule(Long doctorId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        return getByDoctorAndDateRange(doctorId, weekStart, weekEnd);
    }

    // Vô hiệu hoá ca làm việc
    @Override
    @Transactional
    public DoctorScheduleResponse deactivate(Long id) {
        DoctorSchedule schedule = findByIdOrThrow(id);
        schedule.setActive(false);

        // Block tất cả slot AVAILABLE trong ca này
        schedule.getTimeSlots().forEach(slot -> {
            if (slot.getStatus() == SlotStatus.AVAILABLE) {
                slot.setStatus(SlotStatus.BLOCKED);
            }
        });
        timeSlotRepository.saveAll(schedule.getTimeSlots());

        DoctorSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponseWithSlots(saved);
    }



    // Xoá ca làm việc
    @Override
    @Transactional
    public void delete(Long id) {
        DoctorSchedule schedule = findByIdOrThrow(id);

        // Không cho xoá nếu đã có slot được đặt
        boolean hasBookedSlot = schedule.getTimeSlots().stream()
                .anyMatch(slot -> slot.getStatus() == SlotStatus.BOOKED);

        if (hasBookedSlot) {
            throw new IllegalStateException(
                    "Không thể xoá ca làm việc vì đã có lịch hẹn được đặt");
        }

        scheduleRepository.delete(schedule);
    }

    @Transactional
    @Override
    public DoctorScheduleResponse activate(Long id) {
        DoctorSchedule schedule = findByIdOrThrow(id);

        schedule.setActive(true);

        // Mở lại tất cả slot đang BLOCKED
        schedule.getTimeSlots().forEach(slot -> {
            if (slot.getStatus() == SlotStatus.BLOCKED) {
                slot.setStatus(SlotStatus.AVAILABLE);
            }
        });
        timeSlotRepository.saveAll(schedule.getTimeSlots());

        DoctorSchedule saved = scheduleRepository.save(schedule);
        return scheduleMapper.toResponseWithSlots(saved);
    }

    // ===== Private helper =====

    // Sinh danh sách TimeSlot từ DoctorSchedule
    // VD: Ca 08:00 - 12:00, mỗi slot 30 phút
    //     → 08:00-08:30, 08:30-09:00, 09:00-09:30 ... 11:30-12:00
    private List<TimeSlot> generateTimeSlots(DoctorSchedule schedule) {
        List<TimeSlot> slots = new ArrayList<>();
        LocalTime current = schedule.getStartTime();
        LocalTime endTime = schedule.getEndTime();
        int duration = schedule.getSlotDurationMinutes();

        while (current.plusMinutes(duration).compareTo(endTime) <= 0) {
            LocalTime slotEnd = current.plusMinutes(duration);

            TimeSlot slot = TimeSlot.builder()
                    .schedule(schedule)
                    .doctor(schedule.getDoctor())
                    .slotDate(schedule.getWorkDate())
                    .startTime(current)
                    .endTime(slotEnd)
                    .status(SlotStatus.AVAILABLE)
                    .build();

            slots.add(slot);
            current = slotEnd; // Slot tiếp theo bắt đầu từ cuối slot này
        }

        return slots;
    }

    private DoctorSchedule findByIdOrThrow(Long id) {
        return scheduleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy ca làm việc với id: " + id));
    }
}
