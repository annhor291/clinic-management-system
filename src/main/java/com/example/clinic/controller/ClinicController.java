package com.example.clinic.controller;

import com.example.clinic.config.ClinicProperties;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.ClinicInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/clinic")
@RequiredArgsConstructor
public class ClinicController {

    private final ClinicProperties clinicProperties;

    // GET /api/v1/clinic — công khai, dùng cho trang chủ và màn hình "chỉ đường"
    @GetMapping
    public ResponseEntity<ApiResponse<ClinicInfoResponse>> getInfo() {
        return ResponseEntity.ok(ApiResponse.success("Lấy thông tin phòng khám thành công",
                ClinicInfoResponse.from(clinicProperties)));
    }
}
