package com.example.clinic.mapper;

import com.example.clinic.dto.request.ManagedPatientCreateRequest;
import com.example.clinic.dto.request.PatientCreateRequest;
import com.example.clinic.dto.request.PatientUpdateRequest;
import com.example.clinic.dto.response.PatientResponse;
import com.example.clinic.entity.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper {

    // Convert PatientCreateRequest → Patient entity (dùng khi CREATE)
    public Patient toEntity(PatientCreateRequest request) {
        return Patient.builder()
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress())
                .bloodType(request.getBloodType())
                .allergies(request.getAllergies())
                .medicalNotes(request.getMedicalNotes())
                .insuranceNumber(request.getInsuranceNumber())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .build();
    }

    // Convert Patient entity → PatientResponse (dùng khi GET)
    public PatientResponse toResponse(Patient patient) {
        PatientResponse.PatientResponseBuilder builder = PatientResponse.builder()
                .id(patient.getId())
                .fullName(patient.getFullName())
                .phone(patient.getPhone())
                .gender(patient.getGender())
                .dateOfBirth(patient.getDateOfBirth())
                .address(patient.getAddress())
                .bloodType(patient.getBloodType())
                .allergies(patient.getAllergies())
                .medicalNotes(patient.getMedicalNotes())
                .insuranceNumber(patient.getInsuranceNumber())
                .emergencyContactName(patient.getEmergencyContactName())
                .emergencyContactPhone(patient.getEmergencyContactPhone())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .contactEmail(patient.getContactEmail())
                .relationshipLabel(patient.getRelationshipLabel());

        if (patient.getUser() != null) {
            builder.userId(patient.getUser().getId())
                    .username(patient.getUser().getUsername())
                    .email(patient.getUser().getEmail())
                    .isManaged(false);
        } else {
            builder.isManaged(true)
                    .managedByUserId(patient.getManagedBy() != null ? patient.getManagedBy().getId() : null);
        }

        return builder.build();
    }

    // Cập nhật Patient entity từ PatientUpdateRequest (dùng khi UPDATE)
    // Chỉ update field nào khác null → field null giữ nguyên giá trị cũ
    public void updateEntity(Patient patient, PatientUpdateRequest request) {
        if (request.getFullName() != null) {
            patient.setFullName(request.getFullName());
        }
        if (request.getPhone() != null) {
            patient.setPhone(request.getPhone());
        }
        if (request.getGender() != null) {
            patient.setGender(request.getGender());
        }
        if (request.getDateOfBirth() != null) {
            patient.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getAddress() != null) {
            patient.setAddress(request.getAddress());
        }
        if (request.getBloodType() != null) {
            patient.setBloodType(request.getBloodType());
        }
        if (request.getAllergies() != null) {
            patient.setAllergies(request.getAllergies());
        }
        if (request.getMedicalNotes() != null) {
            patient.setMedicalNotes(request.getMedicalNotes());
        }
        if (request.getInsuranceNumber() != null) {
            patient.setInsuranceNumber(request.getInsuranceNumber());
        }
        if (request.getEmergencyContactName() != null) {
            patient.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null) {
            patient.setEmergencyContactPhone(request.getEmergencyContactPhone());
        }
    }

    public Patient toEntity(ManagedPatientCreateRequest request) {
        return Patient.builder()
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .gender(request.getGender())
                .dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress())
                .bloodType(request.getBloodType())
                .allergies(request.getAllergies())
                .medicalNotes(request.getMedicalNotes())
                .insuranceNumber(request.getInsuranceNumber())
                .emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .contactEmail(request.getContactEmail())
                .relationshipLabel(request.getRelationshipLabel())
                .build();
    }
}
