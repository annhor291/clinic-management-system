package com.example.clinic.service.impl;

import com.example.clinic.dto.request.SpecialtyCreateRequest;
import com.example.clinic.dto.request.SpecialtyUpdateRequest;
import com.example.clinic.dto.response.PageResponse;
import com.example.clinic.dto.response.SpecialtyResponse;
import com.example.clinic.entity.Specialty;
import com.example.clinic.exception.DuplicateResourceException;
import com.example.clinic.exception.ResourceNotFoundException;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.SpecialtyRepository;
import com.example.clinic.service.SpecialtyService;
import com.example.clinic.mapper.SpecialtyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpecialtyServiceImpl implements SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final DoctorRepository doctorRepository;
    private final SpecialtyMapper specialtyMapper;

    // Lấy tất cả chuyên khoa đang active — dùng cho dropdown chọn chuyên khoa
    // @Transactional(readOnly = true): tối ưu performance cho các query chỉ đọc
    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyResponse> getAllActive() {
        return specialtyRepository.findByActiveTrue()
                .stream()
                .map(specialty -> {
                    // Đếm số bác sĩ thuộc chuyên khoa này
                    int totalDoctors = (int) doctorRepository.countBySpecialtyId(specialty.getId());
                    return specialtyMapper.toResponse(specialty, totalDoctors);
                })
                .collect(Collectors.toList());
    }

    // Lấy danh sách chuyên khoa có pagination + filter theo keyword và active
    @Override
    @Transactional(readOnly = true)
    public PageResponse<SpecialtyResponse> getAll(String keyword, Boolean active, int page, int size) {
        // Tạo Pageable: trang số page, mỗi trang size phần tử, sort theo tên A→Z
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());

        Page<Specialty> specialtyPage = specialtyRepository.searchSpecialties(keyword, active, pageable);

        // Convert từng Specialty → SpecialtyResponse rồi wrap vào PageResponse
        Page<SpecialtyResponse> responsePage = specialtyPage.map(specialty -> {
            int totalDoctors = (int) doctorRepository.countBySpecialtyId(specialty.getId());
            return specialtyMapper.toResponse(specialty, totalDoctors);
        });

        return PageResponse.of(responsePage);
    }

    // Lấy chi tiết 1 chuyên khoa — ném exception nếu không tìm thấy
    @Override
    @Transactional(readOnly = true)
    public SpecialtyResponse getById(Long id) {
        Specialty specialty = findByIdOrThrow(id);
        int totalDoctors = (int) doctorRepository.countBySpecialtyId(id);
        return specialtyMapper.toResponse(specialty, totalDoctors);
    }

    // Tạo mới chuyên khoa
    @Override
    @Transactional
    public SpecialtyResponse create(SpecialtyCreateRequest request) {
        // Kiểm tra tên chuyên khoa đã tồn tại chưa
        if (specialtyRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Chuyên khoa '" + request.getName() + "' đã tồn tại");
        }

        Specialty specialty = specialtyMapper.toEntity(request);
        Specialty saved = specialtyRepository.save(specialty);
        return specialtyMapper.toResponse(saved, 0);
    }

    // Cập nhật chuyên khoa
    @Override
    @Transactional
    public SpecialtyResponse update(Long id, SpecialtyUpdateRequest request) {
        Specialty specialty = findByIdOrThrow(id);

        // Kiểm tra tên mới có trùng với chuyên khoa khác không (bỏ qua chính nó)
        if (specialtyRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new DuplicateResourceException("Chuyên khoa '" + request.getName() + "' đã tồn tại");
        }

        // Update trực tiếp vào entity → JPA dirty checking tự chạy UPDATE
        specialtyMapper.updateEntity(specialty, request);
        Specialty saved = specialtyRepository.save(specialty);

        int totalDoctors = (int) doctorRepository.countBySpecialtyId(id);
        return specialtyMapper.toResponse(saved, totalDoctors);
    }

    // Xoá chuyên khoa
    @Override
    @Transactional
    public void delete(Long id) {
        Specialty specialty = findByIdOrThrow(id);

        // Không cho xoá nếu còn bác sĩ thuộc chuyên khoa này
        // vì FK constraint sẽ báo lỗi nếu cố xoá
        if (doctorRepository.existsBySpecialtyId(id)) {
            throw new IllegalStateException(
                    "Không thể xoá chuyên khoa '" + specialty.getName() + "' vì vẫn còn bác sĩ thuộc chuyên khoa này"
            );
        }

        specialtyRepository.delete(specialty);
    }

    // ===== Private helper =====

    // Tìm chuyên khoa theo id, ném exception nếu không tìm thấy
    // Tách ra method riêng để tái sử dụng, không lặp code
    private Specialty findByIdOrThrow(Long id) {
        return specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuyên khoa với id: " + id));
    }

}
