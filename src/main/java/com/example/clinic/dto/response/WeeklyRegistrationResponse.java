package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeeklyRegistrationResponse {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private LocalDate weekStartDate;
    private RegistrationStatus status;
    private LocalDateTime submittedAt;
    private Long decidedById;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String rejectionReason;
    private List<DayShiftResponse> days;

    // Cảnh báo mềm (VD: trùng lịch nghỉ đã duyệt) — chỉ có giá trị ở response của submit(),
    // các API khác (getById, list) luôn trả về rỗng
    @Builder.Default
    private List<String> warnings = List.of();
}
