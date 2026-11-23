package com.example.clinic.service;

import com.example.clinic.dto.response.SuggestedSlotResponse;

import java.util.List;

public interface AppointmentSuggestionService {

    // Gợi ý các slot thay thế cho 1 lịch hẹn đã CANCELLED hoặc EXPIRED
    List<SuggestedSlotResponse> suggestAlternatives(Long cancelledAppointmentId);
}
