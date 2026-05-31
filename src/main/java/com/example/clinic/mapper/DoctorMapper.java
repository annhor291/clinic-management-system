package com.example.clinic.mapper;

import com.example.clinic.dto.request.DoctorCreateRequest;
import com.example.clinic.dto.request.DoctorUpdateRequest;
import com.example.clinic.dto.response.DoctorResponse;
import com.example.clinic.entity.Doctor;
import org.springframework.stereotype.Component;

@Component
public class DoctorMapper {

    // Convert DoctorCreateRequest → Doctor entity (dùng khi CREATE)
    public Doctor toEntity(DoctorCreateRequest request) {
        return Doctor.builder()
                .fullName(request.getFullName())
                .title(request.getTitle())
                .experienceYears(request.getExperienceYears())
                .phone(request.getPhone())
                .bio(request.getBio())
                .consultationFee(request.getConsultationFee())
                .active(true)
                .build();
    }

    // Convert Doctor entity → DoctorResponse
    // Lấy thêm thông tin từ user và specialty liên kết
    public DoctorResponse toResponse(Doctor doctor) {
        return DoctorResponse.builder()
                .id(doctor.getId())
                // Thông tin tài khoản
                .userId(doctor.getUser().getId())
                .username(doctor.getUser().getUsername())
                .email(doctor.getUser().getEmail())
                // Thông tin chuyên khoa — trả cả id lẫn name
                // client không cần query thêm để lấy tên chuyên khoa
                .specialtyId(doctor.getSpecialty().getId())
                .specialtyName(doctor.getSpecialty().getName())
                // Thông tin bác sĩ
                .fullName(doctor.getFullName())
                .title(doctor.getTitle())
                .experienceYears(doctor.getExperienceYears())
                .phone(doctor.getPhone())
                .bio(doctor.getBio())
                .consultationFee(doctor.getConsultationFee())
                .active(doctor.isActive())
                .createdAt(doctor.getCreatedAt())
                .updatedAt(doctor.getUpdatedAt())
                .build();
    }

    // Cập nhật Doctor entity từ DoctorUpdateRequest (dùng khi UPDATE)
    // Chỉ update field nào khác null → field null giữ nguyên giá trị cũ
    public void updateEntity(Doctor doctor, DoctorUpdateRequest request) {
        if (request.getFullName() != null) {
            doctor.setFullName(request.getFullName());
        }
        if (request.getTitle() != null) {
            doctor.setTitle(request.getTitle());
        }
        if (request.getExperienceYears() != null) {
            doctor.setExperienceYears(request.getExperienceYears());
        }
        if (request.getPhone() != null) {
            doctor.setPhone(request.getPhone());
        }
        if (request.getBio() != null) {
            doctor.setBio(request.getBio());
        }
        if (request.getConsultationFee() != null) {
            doctor.setConsultationFee(request.getConsultationFee());
        }
        if (request.getActive() != null) {
            doctor.setActive(request.getActive());
        }
        // specialty được Service xử lý riêng vì cần query DB
        // updated_at tự động cập nhật bởi @PreUpdate trong Entity
    }
}
