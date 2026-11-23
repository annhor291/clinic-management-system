package com.example.clinic.triage.rules;

public enum TriageOutcome {
    EMERGENCY,  // dấu hiệu nguy hiểm: gọi cấp cứu, không đặt lịch
    REFER,      // hệ thống không đủ chắc chắn: chuyển cho điều dưỡng / chăm sóc khách hàng
    PASS        // đủ tin cậy để gợi ý chuyên khoa
}
