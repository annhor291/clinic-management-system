package com.example.clinic.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpecialtyCreateRequest {

    @NotBlank(message = "Tên chuyên khoa không được để trống")
    @Size(max = 100, message = "Tên chuyên khoa không được vượt quá 100 ký tự")
    private String name;

    private String description;
}
