package com.example.clinic.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DoctorUpdateRequest {

    // specialtyId nullable — chỉ đổi chuyên khoa khi có giá trị
    private Long specialtyId;

    @Size(max = 100, message = "Họ tên không được vượt quá 100 ký tự")
    private String fullName;

    @Size(max = 50, message = "Chức danh không được vượt quá 50 ký tự")
    private String title;

    @Min(value = 0, message = "Số năm kinh nghiệm không hợp lệ")
    @Max(value = 60, message = "Số năm kinh nghiệm không hợp lệ")
    private Integer experienceYears;

    @Pattern(regexp = "^[0-9]{10,11}$", message = "Số điện thoại không hợp lệ")
    private String phone;

    private String bio;

    @Min(value = 0, message = "Phí khám không được âm")
    private BigDecimal consultationFee;

    // active nullable — chỉ update khi có giá trị
    private Boolean active;

}
