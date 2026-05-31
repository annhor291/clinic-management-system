package com.example.clinic.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SpecialtyUpdateRequest {

    @Size(max = 100, message = "Tên chuyên khoa không được vượt quá 100 ký tự")
    private String name;

    private String description;

    // active nullable — chỉ update khi có giá trị
    private Boolean active;
}
