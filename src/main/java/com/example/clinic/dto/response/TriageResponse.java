package com.example.clinic.dto.response;

import com.example.clinic.triage.engine.TriageFlowEngine;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

// Kết quả của một bước gợi ý. Tuỳ status mà một số trường có hoặc không (NON_NULL):
//   QUESTION  → question
//   EMERGENCY → message (+ supportMessage khi có ý nghĩ tự làm hại)
//   REFER     → message, reasons, reasonCodes
//   PASS      → message, specialty, doctors, disclaimer
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriageResponse {

    private TriageFlowEngine.Status status;

    // true khi nhóm tuổi được lấy tự động từ ngày sinh trong hồ sơ (client không cần hỏi câu về tuổi)
    private boolean ageFromProfile;

    private QuestionDto question;

    private String message;
    private String supportMessage;
    private List<String> reasons;
    private List<String> reasonCodes;

    private SpecialtyDto specialty;
    private List<DoctorDto> doctors;
    private String disclaimer;

    // Luôn trả kèm để màn hình nào cũng có thể hiện số cần gọi
    private String emergencyNumber;
    private String clinicPhone;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionDto {
        private String code;
        private String text;
        private String type;            // SINGLE | MULTI
        private List<OptionDto> options;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionDto {
        private String code;
        private String label;
        private boolean exclusive;      // true = đáp án "không có", không chọn cùng đáp án khác
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SpecialtyDto {
        private Long id;
        private String code;
        private String name;
    }

    // Chỉ thông tin công khai của bác sĩ — không có email, số điện thoại, tài khoản
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DoctorDto {
        private Long id;
        private String fullName;
        private String title;
        private Integer experienceYears;
        private BigDecimal consultationFee;
        private NextSlotDto nextSlot;   // null = chưa có lịch trống trong thời gian tìm kiếm
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class NextSlotDto {
        private Long slotId;
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
    }
}
