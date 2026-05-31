package com.example.clinic.dto.request;

import com.example.clinic.entity.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PatientCreateRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @Pattern(regexp = "^[0-9]{10,11}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    private Gender gender;

    @Past(message = "Ngày sinh phải là ngày trong quá khứ")
    private LocalDate dateOfBirth;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Pattern(regexp = "^(A|B|AB|O)[+-]$", message = "Nhóm máu không hợp lệ (VD: A+, B-, AB+, O-)")
    private String bloodType;

    private String allergies;

    private String medicalNotes;

    @Size(max = 20, message = "Số BHYT không được vượt quá 20 ký tự")
    private String insuranceNumber;

    @Size(max = 100, message = "Tên người liên hệ không được vượt quá 100 ký tự")
    private String emergencyContactName;

    @Pattern(regexp = "^[0-9]{10,11}$", message = "Số điện thoại người liên hệ không hợp lệ")
    private String emergencyContactPhone;
}
