package com.example.clinic.booking.rules;

import com.example.clinic.booking.engine.BookingContext;
import com.example.clinic.booking.engine.BookingRule;
import com.example.clinic.booking.engine.BookingRuleViolationException;
import org.springframework.stereotype.Component;

// Bệnh nhân không được có 2 lịch hẹn active (PENDING/CONFIRMED) trùng khung giờ,
// kể cả với bác sĩ khác — kiểm tra theo time-range, không phụ thuộc slotId
@Component
public class PatientOverlapRule implements BookingRule {

    @Override
    public void validate(BookingContext context) {
        if (context.hasOverlappingAppointment()) {
            throw new BookingRuleViolationException("Bạn đã có lịch hẹn khác trùng khung giờ này");
        }
    }

    @Override
    public int getOrder() {
        return 40;
    }
}
