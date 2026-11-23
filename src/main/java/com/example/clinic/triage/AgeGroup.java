package com.example.clinic.triage;

import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

// Đổi ngày sinh trong hồ sơ thành mã nhóm tuổi của câu hỏi AGE trong bộ luật.
// Chỉ dùng NGÀY SINH — không đọc bất kỳ thông tin y tế nào khác của hồ sơ.
public final class AgeGroup {

    // Ngày sinh vô lý (nhập sai) thì coi như không có, để hệ thống hỏi lại thay vì đoán
    private static final int MAX_PLAUSIBLE_AGE = 130;

    private AgeGroup() {}

    public static Optional<String> of(LocalDate dateOfBirth, LocalDate today) {
        if (dateOfBirth == null || dateOfBirth.isAfter(today)) {
            return Optional.empty();
        }
        int years = Period.between(dateOfBirth, today).getYears();
        if (years > MAX_PLAUSIBLE_AGE) {
            return Optional.empty();
        }
        if (years < 1) return Optional.of("INFANT");
        if (years <= 14) return Optional.of("CHILD");
        if (years <= 59) return Optional.of("ADULT");
        return Optional.of("SENIOR");
    }
}
