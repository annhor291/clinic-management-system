package com.example.clinic.service.impl;

import com.example.clinic.dto.request.DoctorCreateRequest;
import com.example.clinic.dto.request.DoctorUpdateRequest;
import com.example.clinic.dto.response.DoctorResponse;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.Specialty;
import com.example.clinic.entity.User;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.SpecialtyRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.security.SecurityUtil;
import com.example.clinic.service.DoctorService;
import com.example.clinic.mapper.DoctorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final DoctorMapper doctorMapper;

    // Lấy danh sách bác sĩ có pagination + filter
    @Override
    @Transactional(readOnly = true)
    public PageResponse<DoctorResponse> getAll(
            String keyword, Long specialtyId, Boolean active, int page, int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());

        // searchDoctors đã JOIN FETCH specialty → không bị N+1 query
        Page<Doctor> doctorPage = doctorRepository.searchDoctors(keyword, specialtyId, active, pageable);

        Page<DoctorResponse> responsePage = doctorPage.map(doctorMapper::toResponse);

        return PageResponse.of(responsePage);
    }

    // Lấy chi tiết 1 bác sĩ theo id
    @Override
    @Transactional(readOnly = true)
    public DoctorResponse getById(Long id) {
        Doctor doctor = findByIdOrThrow(id);
        return doctorMapper.toResponse(doctor);
    }

    // Lấy thông tin bác sĩ theo user_id
    @Override
    @Transactional(readOnly = true)
    public DoctorResponse getByUserId(Long userId) {
        Doctor doctor = doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy hồ sơ bác sĩ với user id: " + userId));
        return doctorMapper.toResponse(doctor);
    }

    // Tạo mới bác sĩ
    @Override
    @Transactional
    public DoctorResponse create(Long userId, DoctorCreateRequest request) {
        // Kiểm tra user tồn tại không
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy user với id: " + userId));

        // Kiểm tra user này đã có hồ sơ bác sĩ chưa
        if (doctorRepository.existsByUserId(userId)) {
            throw new DuplicateResourceException("User này đã có hồ sơ bác sĩ");
        }

        // Kiểm tra chuyên khoa tồn tại không
        Specialty specialty = specialtyRepository.findById(request.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy chuyên khoa với id: " + request.getSpecialtyId()));

        // Convert request → entity rồi gán user và specialty
        Doctor doctor = doctorMapper.toEntity(request);
        doctor.setUser(user);
        doctor.setSpecialty(specialty);

        Doctor saved = doctorRepository.save(doctor);
        return doctorMapper.toResponse(saved);
    }

    // Cập nhật thông tin bác sĩ
    @Override
    @Transactional
    public DoctorResponse update(Long id, DoctorUpdateRequest request) {
        Doctor doctor = findByIdOrThrow(id);

        doctorMapper.updateEntity(doctor, request);

        if (request.getSpecialtyId() != null) {

            Specialty specialty = specialtyRepository
                    .findById(request.getSpecialtyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Không tìm thấy chuyên khoa với id: "
                                    + request.getSpecialtyId()));

            doctor.setSpecialty(specialty);
        }

        Doctor saved = doctorRepository.save(doctor);
        return doctorMapper.toResponse(saved);
    }



    // Xoá bác sĩ
    @Override
    @Transactional
    public void delete(Long id) {
        Doctor doctor = findByIdOrThrow(id);
        doctorRepository.delete(doctor);
    }

    // Kích hoạt bác sĩ
    // Dùng thay cho xoá để giữ lại lịch sử dữ liệu
    @Override
    @Transactional
    public DoctorResponse activate(Long id) {
        Doctor doctor = findByIdOrThrow(id);

        if (doctor.isActive()) {
            throw new IllegalStateException("Bác sĩ này đang ở trạng thái hoạt động");
        }

        doctor.setActive(true);
        Doctor saved = doctorRepository.save(doctor);
        return doctorMapper.toResponse(saved);
    }

    // Vô hiệu hoá bác sĩ
    // Dùng thay cho xoá để giữ lại lịch sử dữ liệu
    @Override
    @Transactional
    public DoctorResponse deactivate(Long id) {
        Doctor doctor = findByIdOrThrow(id);

        if (!doctor.isActive()) {
            throw new IllegalStateException("Bác sĩ này đang ở trạng thái vô hiệu hoá");
        }

        doctor.setActive(false);
        Doctor saved = doctorRepository.save(doctor);
        return doctorMapper.toResponse(saved);
    }

    // Lấy hồ sơ bác sĩ của người đang đăng nhập
    // Tái sử dụng logic getByUserId() để tránh duplicate code
    @Override
    @Transactional(readOnly = true)
    public DoctorResponse getMe() {
        try {
            return getByUserId(SecurityUtil.getCurrentUserId());

        } catch (ResourceNotFoundException e) {
            throw new ResourceNotFoundException("Bạn chưa có hồ sơ bác sĩ");
        }
    }

    // ===== Private helper =====

    private Doctor findByIdOrThrow(Long id) {
        return doctorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bác sĩ với id: " + id));
    }
}
