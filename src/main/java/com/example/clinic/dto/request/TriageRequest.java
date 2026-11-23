package com.example.clinic.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TriageRequest {

    // Không bắt buộc. Có thì phải đăng nhập và là hồ sơ của chính mình (hoặc người thân mình quản lý):
    // hệ thống dùng NGÀY SINH của hồ sơ để tự biết nhóm tuổi, không hỏi lại câu về tuổi.
    private Long patientId;

    // Các câu đã trả lời: mã câu hỏi → các mã đáp án đã chọn. Rỗng = bắt đầu từ câu đầu tiên.
    // Hệ thống không lưu gì: mỗi lần gọi client gửi lại TOÀN BỘ câu trả lời tới lúc đó.
    @NotNull(message = "Thiếu danh sách câu trả lời")
    @Size(max = 30, message = "Quá nhiều câu trả lời")
    private Map<String, List<String>> answers;
}
