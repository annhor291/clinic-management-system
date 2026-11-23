package com.example.clinic.service;

import com.example.clinic.dto.request.TriageRequest;
import com.example.clinic.dto.response.TriageResponse;

public interface TriageService {

    // Nhận các câu đã trả lời, trả về bước tiếp theo: câu hỏi kế tiếp, hoặc kết quả cuối
    // (cấp cứu / chuyển nhân viên tư vấn / gợi ý chuyên khoa). Không lưu bất kỳ dữ liệu nào.
    TriageResponse next(TriageRequest request);
}
