package com.example.clinic.controller;

import com.example.clinic.dto.request.TriageRequest;
import com.example.clinic.dto.response.ApiResponse;
import com.example.clinic.dto.response.TriageResponse;
import com.example.clinic.service.TriageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/triage")
@RequiredArgsConstructor
public class TriageController {

    private final TriageService triageService;

    // POST /api/v1/triage/next — công khai, không lưu gì.
    // Client gửi toàn bộ câu đã trả lời, nhận lại câu hỏi kế tiếp hoặc kết quả cuối.
    // patientId (nếu có) được TriageService kiểm tra quyền, nên endpoint này không cần đăng nhập.
    @PostMapping("/next")
    public ResponseEntity<ApiResponse<TriageResponse>> next(@Valid @RequestBody TriageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Gợi ý chuyên khoa", triageService.next(request)));
    }
}
