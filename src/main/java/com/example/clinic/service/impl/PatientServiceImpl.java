package com.example.clinic.service.impl;

import com.example.clinic.booking.config.BookingProperties;
import com.example.clinic.dto.request.ManagedPatientCreateRequest;
import com.example.clinic.dto.request.PatientCreateRequest;
import com.example.clinic.dto.request.PatientUpdateRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.PatientResponse;
import com.example.clinic.entity.Patient;
import com.example.clinic.entity.User;
import com.example.clinic.entity.enums.AppointmentStatus;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.AppointmentRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.PatientService;
import com.example.clinic.mapper.PatientMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PatientServiceImpl implements PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientMapper patientMapper;
    private final BookingProperties bookingProperties;
    private final AppointmentRepository appointmentRepository;

    // Lấy danh sách bệnh nhân có pagination + filter
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PatientResponse> getAll(String keyword, int page, int size) {
        // Sort theo fullName A→Z
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());

        Page<Patient> patientPage = patientRepository.searchPatients(keyword, pageable);

        // map() của Spring Page: convert từng Patient → PatientResponse
        Page<PatientResponse> responsePage = patientPage.map(patientMapper::toResponse);

        return PageResponse.of(responsePage);
    }

    // Lấy chi tiết 1 bệnh nhân theo id
    @Override
    @Transactional(readOnly = true)
    public PatientResponse getById(Long id) {
        Patient patient = findByIdOrThrow(id);

        // Bác sĩ chỉ xem được hồ sơ bệnh nhân đã có lịch hẹn với chính mình
        if (SecurityUtil.isDoctor()) {
            boolean hasRelation = appointmentRepository.existsByDoctorUserIdAndPatientIdAndStatusIn(
                    SecurityUtil.getCurrentUserId(), id,
                    List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED,
                            AppointmentStatus.COMPLETED, AppointmentStatus.NO_SHOW));
            if (!hasRelation) {
                throw new AccessDeniedException("Bạn chỉ có thể xem hồ sơ bệnh nhân đã có lịch hẹn với mình");
            }
        }
        return patientMapper.toResponse(patient);
    }

    // Lấy thông tin bệnh nhân theo user_id
    // Dùng khi bệnh nhân đăng nhập và xem hồ sơ của chính mình
    @Override
    @Transactional(readOnly = true)
    public PatientResponse getByUserId(Long userId) {
        Patient patient = patientRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hồ sơ bệnh nhân với user id: " + userId));
        return patientMapper.toResponse(patient);
    }



    // Tạo mới bệnh nhân
    // userId: id của User đã đăng ký, sẽ liên kết với Patient này
    @Override
    @Transactional
    public PatientResponse create(Long userId, PatientCreateRequest request) {
        // Kiểm tra user tồn tại không
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy user với id: " + userId));

        // Kiểm tra user này đã có hồ sơ bệnh nhân chưa
        if (patientRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("User này đã có hồ sơ bệnh nhân");
        }

        // Kiểm tra số BHYT có bị trùng không
        if (request.getInsuranceNumber() != null &&
                patientRepository.existsByInsuranceNumber(request.getInsuranceNumber())) {
            throw new DuplicateResourceException(
                    "Số BHYT '" + request.getInsuranceNumber() + "' đã được sử dụng");
        }

        // Convert request → entity rồi gán user
        Patient patient = patientMapper.toEntity(request);
        patient.setUser(user);

        Patient saved = patientRepository.save(patient);

        // Tên tài khoản = họ tên trong hồ sơ chính chủ
        user.setUsername(saved.getFullName());
        userRepository.save(user);

        return patientMapper.toResponse(saved);
    }

    // Cập nhật thông tin bệnh nhân
    @Override
    @Transactional
    public PatientResponse update(Long id, PatientUpdateRequest request) {
        Patient patient = findByIdOrThrow(id);

        // Kiểm tra số BHYT mới có trùng với bệnh nhân khác không
        // existsByInsuranceNumberAndIdNot: bỏ qua chính bệnh nhân đang update
        if (request.getInsuranceNumber() != null &&
                patientRepository.existsByInsuranceNumberAndIdNot(request.getInsuranceNumber(), id)) {
            throw new DuplicateResourceException(
                    "Số BHYT '" + request.getInsuranceNumber() + "' đã được sử dụng");
        }

        // Update trực tiếp vào entity → JPA dirty checking tự chạy UPDATE
        patientMapper.updateEntity(patient, request);
        // Chỉ hồ sơ chính chủ (có user) mới đồng bộ; hồ sơ người thân (user = null) thì bỏ qua
        if (patient.getUser() != null && request.getFullName() != null) {
            patient.getUser().setUsername(patient.getFullName());
            userRepository.save(patient.getUser());
        }
        Patient saved = patientRepository.save(patient);
        return patientMapper.toResponse(saved);
    }

    // Xoá bệnh nhân(không xóa cứng bệnh nhân đã có lịch hẹn)
    @Override
    @Transactional
    public void delete(Long id) {
        Patient patient = findByIdOrThrow(id);
        if (appointmentRepository.existsByPatientId(id)) {
            throw new IllegalStateException("Bệnh nhân đã có lịch hẹn, không thể xóa hồ sơ");
        }
        // Cắt liên kết ngược để cascade từ User không "hồi sinh" bản ghi vừa xóa
        User owner = patient.getUser();
        if (owner != null) {
            owner.setPatient(null);
        }
        patientRepository.delete(patient);
    }

    // Lấy hồ sơ bệnh nhân của chính mình
    // UserId được lấy từ SecurityContext sau khi JWT đã được xác thực
    @Override
    @Transactional(readOnly = true)
    public PatientResponse getMe() {
        try {
            return getByUserId(SecurityUtil.getCurrentUserId());

        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Bạn chưa có hồ sơ bệnh nhân");
        }
    }

    // Bệnh nhân tự tạo hồ sơ của chính mình sau khi đăng ký
    @Override
    @Transactional
    public PatientResponse createMe(PatientCreateRequest request) {
        return create(SecurityUtil.getCurrentUserId(), request);   // đã kiểm tra trùng hồ sơ và trùng BHYT
    }

    // Bệnh nhân tự sửa hồ sơ của chính mình
    @Override
    @Transactional
    public PatientResponse updateMe(PatientUpdateRequest request) {
        Patient patient = patientRepository.findByUserId(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Bạn chưa có hồ sơ bệnh nhân"));
        return update(patient.getId(), request);
    }

    @Override
    @Transactional
    public PatientResponse createManaged(ManagedPatientCreateRequest request) {
        User currentUser = userRepository.findById(SecurityUtil.getCurrentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản hiện tại"));

        long currentCount = patientRepository.countByManagedByIdAndUserIsNull(currentUser.getId());
        if (currentCount >= bookingProperties.getMaxManagedPatients()) {
            throw new IllegalStateException(
                    "Bạn đã đạt giới hạn " + bookingProperties.getMaxManagedPatients() + " hồ sơ người thân");
        }

        if (request.getInsuranceNumber() != null &&
                patientRepository.existsByInsuranceNumber(request.getInsuranceNumber())) {
            throw new DuplicateResourceException(
                    "Số BHYT '" + request.getInsuranceNumber() + "' đã được sử dụng");
        }

        Patient patient = patientMapper.toEntity(request);
        patient.setManagedBy(currentUser);
        // user để null — đây chính là điểm khác biệt với create() công khai

        Patient saved = patientRepository.save(patient);
        return patientMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<PatientResponse> getMyManagedPatients() {
        Long currentUserId = SecurityUtil.getCurrentUserId();
        return patientRepository.findByManagedByIdAndUserIsNull(currentUserId)
                .stream().map(patientMapper::toResponse).toList();
    }

    // ===== Private helper =====

    private Patient findByIdOrThrow(Long id) {
        return patientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bệnh nhân với id: " + id));
    }

}
