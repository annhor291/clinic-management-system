package com.example.clinic.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestedSlotResponse {

    private Long timeSlotId;
    private Long doctorId;
    private String doctorName;
    private String doctorTitle;
    private String specialtyName;
    private BigDecimal consultationFee;
    private LocalDate slotDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private int score;
    private List<String> reasons;   // VD: "Cùng bác sĩ", "Cùng khung giờ"
}
