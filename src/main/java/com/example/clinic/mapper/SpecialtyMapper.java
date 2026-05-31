package com.example.clinic.mapper;

import com.example.clinic.dto.request.SpecialtyCreateRequest;
import com.example.clinic.dto.request.SpecialtyUpdateRequest;
import com.example.clinic.dto.response.SpecialtyResponse;
import com.example.clinic.entity.Specialty;
import org.springframework.stereotype.Component;

@Component
public class SpecialtyMapper {

    // Convert SpecialtyCreateRequest → Specialty entity (dùng khi CREATE)
    public Specialty toEntity(SpecialtyCreateRequest request) {
        return Specialty.builder()
                .name(request.getName())
                .description(request.getDescription())
                .active(true)
                .build();
    }

    // Convert Specialty entity → SpecialtyResponse
    // totalDoctors mặc định = 0, Service sẽ tính toán và gán lại sau
    public SpecialtyResponse toResponse(Specialty specialty) {
        return SpecialtyResponse.builder()
                .id(specialty.getId())
                .name(specialty.getName())
                .description(specialty.getDescription())
                .active(specialty.isActive())
                .totalDoctors(0)
                .createdAt(specialty.getCreatedAt())
                .build();
    }

    // Convert Specialty entity → SpecialtyResponse kèm số lượng bác sĩ
    // Service truyền totalDoctors vào sau khi đếm từ DoctorRepository
    public SpecialtyResponse toResponse(Specialty specialty, int totalDoctors) {
        return SpecialtyResponse.builder()
                .id(specialty.getId())
                .name(specialty.getName())
                .description(specialty.getDescription())
                .active(specialty.isActive())
                .totalDoctors(totalDoctors)
                .createdAt(specialty.getCreatedAt())
                .build();
    }

    // Cập nhật Specialty entity từ SpecialtyUpdateRequest (dùng khi UPDATE)
    // Chỉ update field nào khác null → field null giữ nguyên giá trị cũ
    public void updateEntity(Specialty specialty, SpecialtyUpdateRequest request) {
        if (request.getName() != null) {
            specialty.setName(request.getName());
        }
        if (request.getDescription() != null) {
            specialty.setDescription(request.getDescription());
        }
        if (request.getActive() != null) {
            specialty.setActive(request.getActive());
        }
    }
}
