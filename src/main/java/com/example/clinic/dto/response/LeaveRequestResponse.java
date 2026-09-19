package com.example.clinic.dto.response;

import com.example.clinic.entity.enums.LeaveStatus;
import com.example.clinic.entity.enums.LeaveType;
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
public class LeaveRequestResponse {

    private Long id;
    private Long doctorId;
    private String doctorName;
    private LeaveType type;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LeaveStatus status;
    private LocalDateTime submittedAt;
    private Long decidedById;
    private String decidedByName;
    private LocalDateTime decidedAt;
    private String rejectionReason;

    // Cảnh báo mềm — chỉ có giá trị ở response của submit()
    @Builder.Default
    private List<String> warnings = List.of();
}
