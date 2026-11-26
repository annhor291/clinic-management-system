package com.example.clinic.service.impl;

import com.example.clinic.booking.availability.DoctorAvailabilityGateway;
import com.example.clinic.booking.availability.PatientAvailabilityGateway;
import com.example.clinic.booking.availability.PatientScheduleCheckResult;
import com.example.clinic.booking.config.BookingProperties;
import com.example.clinic.booking.engine.BookingContext;
import com.example.clinic.booking.engine.BookingRuleEngine;
import com.example.clinic.booking.event.*;
import com.example.clinic.dto.request.AppointmentRequest;
import com.example.clinic.dto.request.CancelRequest;
import com.example.clinic.dto.request.RescheduleRequest;
import com.example.clinic.dto.response.AppointmentResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.Patient;
import com.example.clinic.entity.TimeSlot;
import com.example.clinic.entity.enums.*;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.*;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.AppointmentService;
import com.example.clinic.mapper.AppointmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final AppointmentMapper appointmentMapper;
    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityGateway doctorAvailabilityGateway;
    private final BookingRuleEngine bookingRuleEngine;
    private final PatientAvailabilityGateway patientAvailabilityGateway;
    private final BookingProperties bookingProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final PaymentRepository paymentRepository;


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

        // 2. Ownership check
        // PATIENT chỉ được đặt lịch cho chính mình
        // ADMIN và RECEPTIONIST có thể đặt lịch thay cho bệnh nhân
        if (SecurityUtil.isPatient()) {
            if (!patient.isOwnedByUser(SecurityUtil.getCurrentUserId())) {
                throw new AccessDeniedException("Bạn chỉ có thể đặt lịch cho chính mình" +
                        " hoặc người thân đang quản lý");
            }
        }

        // 3. Khoá slot lại để tránh double-booking (PESSIMISTIC_WRITE)
        TimeSlot slot = doctorAvailabilityGateway.lockSlot(request.getTimeSlotId());

        // 4. Tính sẵn dữ liệu cần thiết rồi đóng gói vào BookingContext
        LocalDateTime slotDateTime = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime());
        boolean hasConflict = doctorAvailabilityGateway.hasConflictingAppointment(request.getTimeSlotId());

        PatientScheduleCheckResult scheduleCheck = patientAvailabilityGateway.check(
                patient.getId(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), null);

        BookingContext context = new BookingContext(
                slot.getDoctor().getId(),
                patient.getId(),
                slot.getSlotDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                null,
                LocalDateTime.now(),
                slot.getStatus(),
                hasConflict,
                scheduleCheck.hasOverlap(),
                scheduleCheck.hasBufferViolation()
        );

        // 5. Chạy toàn bộ Business Rule
        bookingRuleEngine.validate(context);

        // 6. Đánh dấu slot đã được đặt
        slot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(slot);

        // 7. Tạo lịch hẹn mới
        Appointment appointment = Appointment.builder()
                .bookingCode(generatePlaceholder())
                .patient(patient)
                .doctor(slot.getDoctor())
                .timeSlot(slot)
                .appointmentTime(slotDateTime)
                .status(AppointmentStatus.PENDING)
                .note(request.getNote())
                .build();

        // 8. Save lần 1 → DB sinh id AUTO INCREMENT
        Appointment saved = appointmentRepository.save(appointment);

        // 9. Sinh bookingCode thật từ id
        // id là AUTO INCREMENT của DB → unique tuyệt đối
        // Không bị reset sau restart, không duplicate khi multi-instance
        saved.setBookingCode(generateBookingCode(saved.getId()));

        // 10. Save lần 2 → cập nhật bookingCode thật
        saved = appointmentRepository.save(saved);

        eventPublisher.publishEvent(new AppointmentBookedEvent(saved.getId()));

        return appointmentMapper.toResponse(saved);
    }

    // ===== LẤY CHI TIẾT LỊCH HẸN =====
    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getById(Long id) {
        Appointment appointment = findByIdOrThrow(id);
        validateViewPermission(appointment);
        return appointmentMapper.toResponse(appointment);
    }

    // ===== LẤY LỊCH HẸN THEO BOOKING CODE =====
    @Override
    @Transactional(readOnly = true)
    public AppointmentResponse getByBookingCode(String bookingCode) {
        Appointment appointment = appointmentRepository.findByBookingCode(bookingCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch hẹn với mã: " + bookingCode));
        validateViewPermission(appointment);
        return appointmentMapper.toResponse(appointment);
    }

    // ===== LẤY DANH SÁCH LỊCH HẸN CỦA BỆNH NHÂN =====
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getByPatient(Long patientId, int page, int size) {
        // Bác sĩ không tra lịch theo bệnh nhân; xem lịch của mình qua danh sách theo bác sĩ
        if (SecurityUtil.isDoctor()) {
            throw new AccessDeniedException("Bác sĩ chỉ có thể xem lịch hẹn của chính mình");
        }
        // Patient chỉ được xem lịch hẹn của chin mình
        if (SecurityUtil.isPatient()) {
            Patient patient = patientRepository.findById(patientId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bệnh nhân"));
            if (!patient.isOwnedByUser(SecurityUtil.getCurrentUserId())) {
                throw new AccessDeniedException("Bạn chỉ có thể xem lịch của chính mình hoặc người thân đang quản lý");
            }
        }
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
        // Bệnh nhân không được xem danh sách lịch hẹn theo bác sĩ (chứa thông tin bệnh nhân khác)
        if (SecurityUtil.isPatient()) {
            throw new AccessDeniedException("Bạn không có quyền xem lịch hẹn của bác sĩ");
        }
        // DOCTOR chỉ được xem lịch hẹn của chính mình
        if (SecurityUtil.isDoctor()) {
            Doctor doctor = doctorRepository.findById(doctorId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy bác sĩ với id: " + doctorId));
            if (!doctor.getUser().getId()
                    .equals(SecurityUtil.getCurrentUserId())) {

                throw new AccessDeniedException(
                        "Bạn chỉ có thể xem lịch hẹn của chính mình");
            }
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<AppointmentResponse> result = appointmentRepository
                .findByDoctorIdOrderByAppointmentTimeDesc(doctorId, pageable)
                .map(appointmentMapper::toResponse);
        return PageResponse.of(result);
    }

    // ===== LỊCH HẸN CỦA TÔI (theo vai trò người đăng nhập) =====
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> getMyAppointments(int page, int size) {
        Long userId = SecurityUtil.getCurrentUserId();

        if (SecurityUtil.isPatient()) {
            // Gồm hồ sơ của chính mình và các hồ sơ người thân đang quản lý
            List<Long> patientIds = new java.util.ArrayList<>();
            patientRepository.findByUserId(userId).ifPresent(p -> patientIds.add(p.getId()));
            patientRepository.findByManagedByIdAndUserIsNull(userId)
                    .forEach(p -> patientIds.add(p.getId()));

            if (patientIds.isEmpty()) {
                // Chưa có hồ sơ nào → chưa có lịch hẹn
                return PageResponse.of(Page.<AppointmentResponse>empty(PageRequest.of(page, size)));
            }
            Page<AppointmentResponse> result = appointmentRepository
                    .findByPatientIdInOrderByAppointmentTimeDesc(patientIds, PageRequest.of(page, size))
                    .map(appointmentMapper::toResponse);
            return PageResponse.of(result);
        }

        if (SecurityUtil.isDoctor()) {
            Doctor doctor = doctorRepository.findByUserId(userId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Tài khoản này chưa có hồ sơ bác sĩ"));
            return getByDoctor(doctor.getId(), page, size);
        }

        throw new AccessDeniedException(
                "Chỉ bệnh nhân và bác sĩ có danh sách lịch hẹn cá nhân; admin/lễ tân dùng chức năng tìm kiếm");
    }

    // ===== TÌM KIẾM LỊCH HẸN (ADMIN) =====
    @Override
    @Transactional(readOnly = true)
    public PageResponse<AppointmentResponse> search(
            Long patientId, Long doctorId, AppointmentStatus status,
            LocalDateTime fromDate, LocalDateTime toDate, int page, int size) {
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Bạn không có quyền tìm kiếm toàn bộ lịch hẹn");
        }
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
        if (!SecurityUtil.isAdminOrReceptionist()) {
            throw new AccessDeniedException("Chỉ lễ tân hoặc admin mới có thể xác nhận lịch hẹn");
        }

        Appointment appointment = findByIdOrThrow(id);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalStateException("Chỉ có thể xác nhận lịch hẹn đang ở trạng thái PENDING");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);

        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentConfirmedEvent(saved.getId()));

        return appointmentMapper.toResponse(saved);
    }

    // ===== ĐÁNH DẤU HOÀN THÀNH =====
    @Override
    @Transactional
    public AppointmentResponse complete(Long id) {
        Appointment appointment = findByIdOrThrow(id);

        if (!SecurityUtil.isAdminOrReceptionist()) {
            if (!SecurityUtil.isDoctor()) {
                throw new AccessDeniedException("Chỉ bác sĩ hoặc admin/lễ tân mới có thể đánh dấu hoàn thành");
            }
            if (!appointment.getDoctor().getUser().getId().equals(SecurityUtil.getCurrentUserId())) {
                throw new AccessDeniedException("Bạn chỉ có thể đánh dấu hoàn thành lịch hẹn của chính mình");
            }
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ có thể đánh dấu hoàn thành lịch hẹn đang ở trạng thái CONFIRMED");
        }

        if (appointment.getAppointmentTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Không thể đánh dấu hoàn thành khi giờ hẹn chưa diễn ra");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);

        TimeSlot slot = appointment.getTimeSlot();
        slot.setStatus(SlotStatus.COMPLETED);
        timeSlotRepository.save(slot);

        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentCompletedEvent(saved.getId()));

        return appointmentMapper.toResponse(saved);
    }

    // ===== ĐÁNH DẤU KHÔNG ĐẾN KHÁM =====
    @Override
    @Transactional
    public AppointmentResponse markNoShow(Long id) {
        Appointment appointment = findByIdOrThrow(id);

        if (!SecurityUtil.isAdminOrReceptionist()) {
            if (!SecurityUtil.isDoctor()) {
                throw new AccessDeniedException("Chỉ bác sĩ hoặc admin/lễ tân mới có thể đánh dấu không đến khám");
            }
            if (!appointment.getDoctor().getUser().getId().equals(SecurityUtil.getCurrentUserId())) {
                throw new AccessDeniedException("Bạn chỉ có thể thao tác trên lịch hẹn của chính mình");
            }
        }

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Chỉ có thể đánh dấu không đến khám cho lịch hẹn đang ở trạng thái CONFIRMED");
        }

        if (appointment.getAppointmentTime().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Không thể đánh dấu không đến khám khi giờ hẹn chưa diễn ra");
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);

        TimeSlot slot = appointment.getTimeSlot();
        slot.setStatus(SlotStatus.COMPLETED); // giờ hẹn đã qua, slot không còn khả dụng để đặt lại
        timeSlotRepository.save(slot);

        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentNoShowEvent(saved.getId()));

        return appointmentMapper.toResponse(saved);
    }

    // ===== TÌM LỊCH HẸN PENDING QUÁ HẠN =====
    @Override
    @Transactional(readOnly = true)
    public List<Long> findExpirablePendingIds() {
        LocalDateTime now = LocalDateTime.now();
        return appointmentRepository.findExpirablePendingIds(
                now.minusHours(bookingProperties.getConfirmMaxHours()),
                now.plusMinutes(bookingProperties.getConfirmBeforeStartMinutes()));
    }

    // ===== CHUYỂN 1 LỊCH HẸN SANG EXPIRED =====
    @Override
    @Transactional
    public void expireOne(Long appointmentId) {
        // Khoá dòng lịch hẹn: tuần tự hoá với tạo/xử lý thanh toán (cùng thứ tự khoá appointment → payment)
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId).orElse(null);
        if (appointment == null) {
            return; // đã bị xử lý ở nơi khác, bỏ qua
        }

        // Kiểm tra lại trạng thái — có thể đã được confirm/cancel giữa lúc lấy danh sách và lúc xử lý
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            return;
        }

        // Quy tắc A: khách đang trong phiên thanh toán còn hạn thì chưa thu hồi lịch.
        // Khi giao dịch hết hạn (PaymentExpiryScheduler) lịch sẽ được expire ở lượt quét sau.
        if (paymentRepository.existsByAppointmentIdAndStatusAndExpiredAtAfter(
                appointmentId, PaymentStatus.PENDING, LocalDateTime.now())) {
            return;
        }

        appointment.setStatus(AppointmentStatus.EXPIRED);
        appointment.setExpiredAt(LocalDateTime.now());
        appointmentRepository.save(appointment);

        TimeSlot slot = appointment.getTimeSlot();
        if (slot.getStatus() == SlotStatus.BOOKED) {
            slot.setStatus(SlotStatus.AVAILABLE);
            timeSlotRepository.save(slot);
        }

        eventPublisher.publishEvent(new AppointmentExpiredEvent(appointmentId));
    }

    // ===== HỦY LỊCH HẸN =====
    @Override
    @Transactional
    public AppointmentResponse cancel(Long id, CancelRequest request) {
        Appointment appointment = findByIdOrThrow(id);
        CancelledBy cancelledBy = validateCancelPermission(appointment);

        if (appointment.getStatus() != AppointmentStatus.PENDING &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Không thể hủy lịch hẹn ở trạng thái hiện tại");
        }

        // Phía bệnh nhân: chính họ hủy, hoặc admin/lễ tân hủy giúp theo yêu cầu của họ
        boolean onBehalf = request.isOnBehalfOfPatient()
                && (cancelledBy == CancelledBy.ADMIN || cancelledBy == CancelledBy.RECEPTIONIST);
        boolean patientSide = cancelledBy == CancelledBy.PATIENT || onBehalf;

        // Giới hạn "hủy trước X giờ" chỉ áp cho hủy từ phía bệnh nhân và lịch đã CONFIRMED
        if (patientSide && appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            LocalDateTime latestAllowed = appointment.getAppointmentTime()
                    .minusHours(bookingProperties.getCancelWindowHours());
            if (LocalDateTime.now().isAfter(latestAllowed)) {
                throw new IllegalStateException(
                        "Phải hủy lịch trước ít nhất " + bookingProperties.getCancelWindowHours() + " giờ so với giờ hẹn");
            }
        }

        return applyCancellation(appointment, request.getReason(), cancelledBy, !patientSide);
    }


    // ===== HỦY LỊCH HẸN DO BÁC SĨ NGHỈ (nội bộ, không qua CancelWindow) =====
    @Override
    @Transactional
    public AppointmentResponse cancelByClinic(Long id, String reason) {
        Appointment appointment = findByIdOrThrow(id);
        if (appointment.getStatus() != AppointmentStatus.PENDING &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Không thể hủy lịch hẹn ở trạng thái hiện tại");
        }
        return applyCancellation(appointment, reason, getCancelledByFromContext(), true);
    }

    // Chung cho mọi kiểu hủy. Hủy từ phía phòng khám thì KHÓA slot (không cho đặt lại),
// hủy từ phía bệnh nhân thì mở lại slot.
    private AppointmentResponse applyCancellation(Appointment appointment, String reason,
                                                  CancelledBy by, boolean clinicInitiated) {
        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointment.setCancellationReason(reason);
        appointment.setCancelledAt(LocalDateTime.now());
        appointment.setCancelledBy(by);
        appointment.setCancelledByClinic(clinicInitiated);

        TimeSlot slot = appointment.getTimeSlot();
        slot.setStatus(clinicInitiated ? SlotStatus.BLOCKED : SlotStatus.AVAILABLE);
        timeSlotRepository.save(slot);

        Appointment saved = appointmentRepository.save(appointment);
        eventPublisher.publishEvent(new AppointmentCancelledEvent(saved.getId()));
        return appointmentMapper.toResponse(saved);
    }

    // ===== ĐỔI LỊCH HẸN =====

    /*@Override
    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest request) {
        Appointment oldAppointment = findByIdOrThrow(id);

        if (!SecurityUtil.isAdminOrReceptionist()) {
            if (!SecurityUtil.isPatient()) {
                throw new AccessDeniedException("Bạn không có quyền đổi lịch hẹn này");
            }
            if (!oldAppointment.getPatient().isOwnedByUser(SecurityUtil.getCurrentUserId())) {
                throw new AccessDeniedException("Bạn chỉ có thể đổi lịch hẹn của chính mình hoặc người thân đang quản lý");
            }
        }

        if (oldAppointment.getStatus() != AppointmentStatus.PENDING &&
                oldAppointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Không thể đổi lịch hẹn ở trạng thái hiện tại");
        }

        // Giới hạn thời gian tối thiểu — chỉ áp dụng khi lịch cũ đã CONFIRMED
        if (oldAppointment.getStatus() == AppointmentStatus.CONFIRMED) {
            LocalDateTime latestAllowed = oldAppointment.getAppointmentTime()
                    .minusHours(bookingProperties.getRescheduleWindowHours());
            if (LocalDateTime.now().isAfter(latestAllowed)) {
                throw new IllegalStateException(
                        "Phải đổi lịch trước ít nhất " + bookingProperties.getRescheduleWindowHours() + " giờ so với giờ hẹn");
            }
        }

        // Khoá slot mới lại để tránh double-booking
        TimeSlot newSlot = doctorAvailabilityGateway.lockSlot(request.getNewTimeSlotId());

        // Tính sẵn dữ liệu rồi chạy lại TOÀN BỘ Rule Engine — giống hệt lúc book(),
        // chỉ khác excludeAppointmentId = id của lịch cũ để không tự đụng chính mình
        boolean hasConflict = doctorAvailabilityGateway.hasConflictingAppointment(request.getNewTimeSlotId());
        PatientScheduleCheckResult scheduleCheck = patientAvailabilityGateway.check(
                oldAppointment.getPatient().getId(), newSlot.getSlotDate(),
                newSlot.getStartTime(), newSlot.getEndTime(), oldAppointment.getId());

        BookingContext context = new BookingContext(
                newSlot.getDoctor().getId(),
                oldAppointment.getPatient().getId(),
                newSlot.getSlotDate(),
                newSlot.getStartTime(),
                newSlot.getEndTime(),
                oldAppointment.getId(),
                LocalDateTime.now(),
                newSlot.getStatus(),
                hasConflict,
                scheduleCheck.hasOverlap(),
                scheduleCheck.hasBufferViolation()
        );

        bookingRuleEngine.validate(context);

        LocalDateTime newSlotDateTime = LocalDateTime.of(newSlot.getSlotDate(), newSlot.getStartTime());

        // Giải phóng slot cũ → AVAILABLE
        TimeSlot oldSlot = oldAppointment.getTimeSlot();
        oldSlot.setStatus(SlotStatus.AVAILABLE);
        timeSlotRepository.save(oldSlot);

        // Hủy appointment cũ
        oldAppointment.setStatus(AppointmentStatus.CANCELLED);
        oldAppointment.setCancellationReason("Đổi lịch: " + request.getReason());
        oldAppointment.setCancelledAt(LocalDateTime.now());
        oldAppointment.setCancelledBy(getCancelledByFromContext());
        appointmentRepository.save(oldAppointment);

        // Lock slot mới → BOOKED
        newSlot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.save(newSlot);

        // Tạo appointment mới với placeholder booking code
        Appointment newAppointment = Appointment.builder()
                .bookingCode(generatePlaceholder())
                .patient(oldAppointment.getPatient())
                .doctor(newSlot.getDoctor())
                .timeSlot(newSlot)
                .appointmentTime(newSlotDateTime)
                .status(AppointmentStatus.PENDING)
                .note(oldAppointment.getNote())
                .rescheduledFrom(oldAppointment)
                .build();

        Appointment saved = appointmentRepository.save(newAppointment);
        saved.setBookingCode(generateBookingCode(saved.getId()));
        saved = appointmentRepository.save(saved);

        eventPublisher.publishEvent(new AppointmentRescheduledEvent(oldAppointment.getId(), saved.getId()));

        return appointmentMapper.toResponse(saved);
    }*/

    @Override
    @Transactional
    public AppointmentResponse reschedule(Long id, RescheduleRequest request) {
        // Khoá lịch hẹn trước rồi mới tới slot (cùng thứ tự với expireOne và luồng thanh toán)
        Appointment appointment = appointmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lịch hẹn với id: " + id));

        if (!SecurityUtil.isAdminOrReceptionist()) {
            if (!SecurityUtil.isPatient()) {
                throw new AccessDeniedException("Bạn không có quyền đổi lịch hẹn này");
            }
            if (!appointment.getPatient().isOwnedByUser(SecurityUtil.getCurrentUserId())) {
                throw new AccessDeniedException("Bạn chỉ có thể đổi lịch hẹn của chính mình hoặc người thân đang quản lý");
            }
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING &&
                appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new IllegalStateException("Không thể đổi lịch hẹn ở trạng thái hiện tại");
        }

        if (appointment.getStatus() == AppointmentStatus.CONFIRMED) {
            LocalDateTime latestAllowed = appointment.getAppointmentTime()
                    .minusHours(bookingProperties.getRescheduleWindowHours());
            if (LocalDateTime.now().isAfter(latestAllowed)) {
                throw new IllegalStateException(
                        "Phải đổi lịch trước ít nhất " + bookingProperties.getRescheduleWindowHours() + " giờ so với giờ hẹn");
            }
        }

        Long oldSlotId = appointment.getTimeSlot().getId();
        Long newSlotId = request.getNewTimeSlotId();
        if (oldSlotId.equals(newSlotId)) {
            throw new IllegalStateException("Lịch hẹn đang ở đúng khung giờ này");
        }

        // Khoá cả hai slot theo thứ tự id tăng dần để hai lượt đổi chéo nhau không deadlock
        TimeSlot low = doctorAvailabilityGateway.lockSlot(Math.min(oldSlotId, newSlotId));
        TimeSlot high = doctorAvailabilityGateway.lockSlot(Math.max(oldSlotId, newSlotId));
        TimeSlot oldSlot = oldSlotId < newSlotId ? low : high;
        TimeSlot newSlot = oldSlotId < newSlotId ? high : low;

        // Quy tắc phí: đã thanh toán thì chỉ đổi trực tiếp khi phí mới đúng bằng khoản đã trả
        BigDecimal oldFee = normalizeFee(appointment.getDoctor().getConsultationFee());
        BigDecimal newFee = normalizeFee(newSlot.getDoctor().getConsultationFee());
        paymentRepository.findFirstByAppointmentIdAndStatus(id, PaymentStatus.SUCCESS).ifPresent(paid -> {
            if (paid.getAmount().compareTo(newFee) != 0) {
                throw new IllegalStateException("Lịch hẹn đã thanh toán " + paid.getAmount().toPlainString()
                        + "đ, còn phí của bác sĩ mới là " + newFee.toPlainString()
                        + "đ nên không thể đổi trực tiếp. Vui lòng hủy lịch này (hoàn 100%) rồi đặt lịch mới.");
            }
        });

        // Chạy lại toàn bộ Rule Engine như lúc book(), loại trừ chính lịch hẹn này
        boolean hasConflict = doctorAvailabilityGateway.hasConflictingAppointment(newSlotId);
        PatientScheduleCheckResult scheduleCheck = patientAvailabilityGateway.check(
                appointment.getPatient().getId(), newSlot.getSlotDate(),
                newSlot.getStartTime(), newSlot.getEndTime(), appointment.getId());

        bookingRuleEngine.validate(new BookingContext(
                newSlot.getDoctor().getId(),
                appointment.getPatient().getId(),
                newSlot.getSlotDate(),
                newSlot.getStartTime(),
                newSlot.getEndTime(),
                appointment.getId(),
                LocalDateTime.now(),
                newSlot.getStatus(),
                hasConflict,
                scheduleCheck.hasOverlap(),
                scheduleCheck.hasBufferViolation()));

        LocalDateTime oldTime = appointment.getAppointmentTime();

        // Mở lại slot cũ (nếu ca của nó còn hoạt động), giữ slot mới
        oldSlot.setStatus(oldSlot.getSchedule().isActive() ? SlotStatus.AVAILABLE : SlotStatus.BLOCKED);
        newSlot.setStatus(SlotStatus.BOOKED);
        timeSlotRepository.saveAll(List.of(oldSlot, newSlot));

        // Cùng một lịch hẹn: giữ nguyên id, mã, trạng thái và thanh toán; chỉ chuyển slot
        appointment.setTimeSlot(newSlot);
        appointment.setDoctor(newSlot.getDoctor());
        appointment.setAppointmentTime(LocalDateTime.of(newSlot.getSlotDate(), newSlot.getStartTime()));
        appointment.setReminder24hSent(false);
        appointment.setReminder1hSent(false);
        Appointment saved = appointmentRepository.save(appointment);

        eventPublisher.publishEvent(new AppointmentRescheduledEvent(
                saved.getId(), oldTime, oldFee.compareTo(newFee) != 0));

        return appointmentMapper.toResponse(saved);
    }

    private BigDecimal normalizeFee(BigDecimal fee) {
        return fee == null ? BigDecimal.ZERO : fee.setScale(0, RoundingMode.HALF_UP);
    }

    // ===== Private helpers =====

    // Tìm lịch hẹn theo id
    // Nếu không tồn tại sẽ ném ResourceNotFoundException
    private Appointment findByIdOrThrow(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy lịch hẹn với id: " + id));
    }

    // Kiểm tra quyền xem lịch hẹn
    // ADMIN và RECEPTIONIST được xem tất cả
    // DOCTOR chỉ xem lịch của mình
    // PATIENT chỉ xem lịch của mình
    private void validateViewPermission(Appointment appointment) {

        // ADMIN và RECEPTIONIST có toàn quyền xem
        if (SecurityUtil.isAdmin() || SecurityUtil.isReceptionist()) return;
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // DOCTOR chỉ được xem lịch thuộc về mình
        if (SecurityUtil.isDoctor()) {
           if (!appointment.getDoctor().getUser().getId().equals(currentUserId)) {
             throw new AccessDeniedException("Bạn không có quyền xem lịch hẹn này");
           }
        }

        // PATIENT chỉ được xem lịch của chính mình
        else if (SecurityUtil.isPatient()) {
            if (!appointment.getPatient().isOwnedByUser(currentUserId)) {
                throw new AccessDeniedException("Bạn không có quyền xem lịch hẹn này");
            }
        }
    }

    // Kiểm tra quyền hủy lịch hẹn
    // Đồng thời xác định ai là người thực hiện thao tác hủy
    // Không lấy thông tin từ client để tránh giả mạo cancelledBy
    private CancelledBy validateCancelPermission(Appointment appointment) {
        Long currentUserId = SecurityUtil.getCurrentUserId();

        // ADMIN có thể hủy mọi lịch hẹn
        if (SecurityUtil.isAdmin()) return CancelledBy.ADMIN;

        // RECEPTIONIST có thể hủy mọi lịch hẹn
        if (SecurityUtil.isReceptionist()) return CancelledBy.RECEPTIONIST;

        // DOCTOR chỉ được hủy lịch thuộc về mình
        if (SecurityUtil.isDoctor()) {
            if (!appointment.getDoctor().getUser().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Bạn chỉ có thể hủy lịch hẹn của chính mình");
            }
            return CancelledBy.DOCTOR;
        }

        // PATIENT chỉ được hủy lịch của chính mình
        if (SecurityUtil.isPatient()) {
            if (!appointment.getPatient().isOwnedByUser(currentUserId)) {
                throw new AccessDeniedException("Bạn chỉ có thể hủy lịch hẹn của chính mình hoặc người thân đang quản lý");
            }
            return CancelledBy.PATIENT;
        }
        throw new AccessDeniedException("Bạn không có quyền hủy lịch hẹn");
    }

    // Chuyển Role hiện tại thành enum CancelledBy
    // Dùng khi hệ thống tự xác định người hủy từ SecurityContext
    // Thay vì lấy cancelledBy từ request của client
    private CancelledBy getCancelledByFromContext() {
        Role role = SecurityUtil.getCurrentRole();
        return switch (role) {
            case ADMIN -> CancelledBy.ADMIN;
            case DOCTOR -> CancelledBy.DOCTOR;
            case PATIENT -> CancelledBy.PATIENT;
            case RECEPTIONIST -> CancelledBy.RECEPTIONIST;
        };
    }

     // Sinh bookingCode từ id của appointment
     // id là AUTO INCREMENT của DB → unique tuyệt đối
     // Không bị reset sau restart
     // Không duplicate khi multi-instance
     // Không có race condition
    private String generateBookingCode(Long appointmentId) {
        String date = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("APT-%s-%04d", date, appointmentId);
    }

     // Sinh placeholder bookingCode để insert lần đầu
     // Dùng UUID ngắn thay vì "TEMP" cố định
     // -> Tránh DUPLICATE KEY khi nhiều request đồng thời
     // -> Sẽ được replace bằng bookingCode thật sau khi có id
    private String generatePlaceholder() {
        return "PH-" + UUID.randomUUID().toString()
                .substring(0, 8).toUpperCase();
        // VD: PH-A3F2B891 — unique, không bao giờ duplicate
    }

}
