package com.example.clinic.service;

import com.example.clinic.dto.response.SuggestedSlotResponse;

import java.util.List;

public interface AppointmentSuggestionService {

    // Gợi ý slot thay thế cho lịch bị PHÒNG KHÁM hủy (cancelledByClinic);
    // các trường hợp khác ném IllegalStateException
    List<SuggestedSlotResponse> suggestAlternatives(Long cancelledAppointmentId);
}
