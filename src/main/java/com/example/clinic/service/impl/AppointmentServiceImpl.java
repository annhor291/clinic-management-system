package com.example.clinic.service.impl;

import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.request.RescheduleRequest;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.Patient;
import com.example.clinic.entity.TimeSlot;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.entity.enums.CancelledBy;
import com.example.clinic.entity.enums.SlotStatus;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.TimeSlotRepository;
import com.example.clinic.service.AppointmentService;
import com.example.clinic.mapper.AppointmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;


@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AppointmentMapper appointmentMapper;

    // Counter dùng để sinh booking code
    private final AtomicLong bookingCounter = new AtomicLong(0);

    // ===== ĐẶT LỊCH KHÁM =====
    // @Transactional đảm bảo toàn bộ quá trình đặt lịch là 1 transaction
    // Nếu bất kỳ bước nào lỗi → rollback toàn bộ, không bị dữ liệu nửa vời
    @Override
    @Transactional
    public AppointmentResponse book(AppointmentRequest request) {
        // 1. Kiểm tra bệnh nhân tồn tại
        Patient patient = patientRepository.findById(request.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bệnh nhân với id: " + request.getPatientId()));

        // 2. Tìm slot và lock lại để tránh double booking
        // findByIdWithLock: dùng SELECT ... FOR UPDATE để lock row
        // Transaction khác muốn đọc slot này phải chờ transaction này xong
        TimeSlot slot = timeSlotRepository.findByIdWithLock(request.getTimeSlotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy slot với id: " + request.getTimeSlotId()));

        // 3. Kiểm tra slot còn trống không
        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new IllegalStateException(
                    "Slot này đã được đặt hoặc không còn khả dụng");
        }

        // 3.5. Kiểm tra slot đã có appointment active chưa (bỏ qua CANCELLED)
        // Cần thiết vì slot có thể AVAILABLE nhưng vẫn còn appointment cũ chưa xoá
        boolean hasActiveAppointment = appointmentRepository
                .existsByTimeSlotIdAndStatusNot(
                        request.getTimeSlotId(), AppointmentStatus.CANCELLED);
        if (hasActiveAppointment) {
            throw new IllegalStateException("Slot này đã được đặt");
        }


        // 4. Kiểm tra slot trong tương lai không
        LocalDateTime slotDateTime = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        if (slotDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Không thể đặt lịch cho thời gian đã qua");
        }

        // 5. Cập nhật status slot → BOOKED
        slot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(slot);

        // 6. Tạo appointment
        Appointment appointment = Appointment.builder()
                .bookingCode(generateBookingCode())
                .patient(patient)
                .doctor(slot.getDoctor())
                .timeSlot(slot)
                .appointmentTime(slotDateTime)
                .status(AppointmentStatus.PENDING)
                .note(request.getNote())
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        return appointmentMapper.toResponse(saved);
    }

    // ===== LẤY CHI TIẾT LỊCH HẸN =====
    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id) {
        return appointmentMapper.toResponse(findByIdOrThrow(id));
    }

    // ===== LẤY LỊCH HẸN THEO BOOKING CODE =====
    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getByBookingCode(String bookingCode) {
        Appointment appointment = appointmentRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch hẹn với mã: " + bookingCode));
        return appointmentMapper.toResponse(appointment);
    }

    // ===== LẤY DANH SÁCH LỊCH HẸN CỦA BỆNH NHÂN =====
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getByPatient(Long patientId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AppointmentResponse> result = appointmentRepository
                .findByPatientIdOrderByAppointmentTimeDesc(patientId, pageable)
                .map(appointmentMapper::toResponse);
        return PageResponse.of(result);
    }

    // ===== LẤY DANH SÁCH LỊCH HẸN CỦA BÁC SĨ =====
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getByDoctor(Long doctorId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AppointmentResponse> result = appointmentRepository
                .findByDoctorIdOrderByAppointmentTimeDesc(doctorId, pageable)
                .map(appointmentMapper::toResponse);
        return PageResponse.of(result);
    }

    // ===== TÌM KIẾM LỊCH HẸN (ADMIN) =====
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(
            Long patientId, Long doctorId, AppointmentStatus status,
            LocalDateTime fromDate, LocalDateTime toDate,
            int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("appointmentTime").descending());
        Page<AppointmentResponse> result = appointmentRepository
                .searchAppointments(patientId, doctorId, status, fromDate, toDate, pageable)
                .map(appointmentMapper::toResponse);
        return PageResponse.of(result);
    }

    // ===== XÁC NHẬN LỊCH HẸN =====
    @Override
    @Transactional
    public AppointmentResponse confirm(Long id) {
        Appointment appointment = findByIdOrThrow(id);

        // Chỉ PENDING mới được confirm
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Chỉ có thể xác nhận lịch hẹn đang ở trạng thái PENDING");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentMapper.toResponse(appointmentRepository.save(appointment));
    }

    // ===== HỦY LỊCH HẸN =====
    @Override
    @Transactional
    public AppointmentResponse cancel(Long id, CancelRequest request) {
        Appointment appointment = findByIdOrThrow(id);

        // Chỉ PENDING hoặc CONFIRMED mới được hủy
        if (appointment.getStatus() != AppointmentStatus.PENDING &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Không thể hủy lịch hẹn ở trạng thái hiện tại");
        }

        // Cập nhật trạng thái appointment
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(request.getReason());
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelledBy(request.getCancelledBy());

        // Giải phóng slot → AVAILABLE để người khác có thể đặt
        TimeSlot slot = appointment.getTimeSlot();
        slot.setStatus(SlotStatus.AVAILABLE);
        timeSlotRepository.save(slot);

        return appointmentMapper.toResponse(appointmentRepository.save(appointment));
    }

    // ===== ĐỔI LỊCH HẸN =====
    @Override
    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest request) {
        Appointment oldAppointment = findByIdOrThrow(id);

        // Chỉ PENDING hoặc CONFIRMED mới được đổi lịch
        if (oldAppointment.getStatus() != AppointmentStatus.PENDING &&
                oldAppointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Không thể đổi lịch hẹn ở trạng thái hiện tại");
        }

        // Tìm slot mới và lock lại
        TimeSlot newSlot = timeSlotRepository.findByIdWithLock(request.getNewTimeSlotId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy slot với id: " + request.getNewTimeSlotId()));

        // Kiểm tra slot mới còn trống không
        if (newSlot.getStatus() != SlotStatus.AVAILABLE) {
            throw new IllegalStateException("Slot mới đã được đặt hoặc không còn khả dụng");
        }

        // Kiểm tra slot mới trong tương lai không
        LocalDateTime newSlotDateTime = LocalDateTime.of(newSlot.getSlotDate(), newSlot.getStartTime());
        if (newSlotDateTime.isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Không thể đổi sang lịch đã qua");
        }

        // Giải phóng slot cũ → AVAILABLE
        TimeSlot oldSlot = oldAppointment.getTimeSlot();
        oldSlot.setStatus(SlotStatus.AVAILABLE);
        timeSlotRepository.save(oldSlot);

        // Hủy appointment cũ
        oldAppointment.setStatus(AppointmentStatus.CANCELLED);
        oldAppointment.setCancellationReason("Đổi lịch: " + request.getReason());
        oldAppointment.setCancelledAt(LocalDateTime.now());
        oldAppointment.setCancelledBy(CancelledBy.PATIENT);
        appointmentRepository.save(oldAppointment);

        // Lock slot mới → BOOKED
        newSlot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(newSlot);

        // Tạo appointment mới, trỏ về appointment cũ để trace lịch sử
        Appointment newAppointment = Appointment.builder()
                .bookingCode(generateBookingCode())
                .patient(oldAppointment.getPatient())
                .doctor(newSlot.getDoctor())
                .timeSlot(newSlot)
                .appointmentTime(newSlotDateTime)
                .status(AppointmentStatus.PENDING)
                .note(oldAppointment.getNote())
                .rescheduledFrom(oldAppointment)    // ← trỏ về lịch cũ
                .build();

        return appointmentMapper.toResponse(appointmentRepository.save(newAppointment));
    }

    // ===== Private helpers =====

    private Appointment findByIdOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch hẹn với id: " + id));
    }

    // Sinh booking code theo format: APT-YYYYMMDD-XXXX
    // VD: APT-20260517-0001
    private String generateBookingCode() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long count = bookingCounter.incrementAndGet();
        return String.format("APT-%s-%04d", date, count);
    }
}
