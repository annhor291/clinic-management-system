package com.example.clinic.booking.rules;

import com.example.clinic.booking.engine.BookingContext;
import com.example.clinic.booking.engine.BookingRule;
import com.example.clinic.booking.engine.BookingRuleViolationException;
import org.springframework.stereotype.Component;

// Slot của bác sĩ không được có appointment khác (chưa huỷ) đã gắn vào — chống double-booking
@Component
public class DoctorAvailabilityRule implements BookingRule {

    @Override
    public void validate(BookingContext context) {
        if (context.hasConflictingAppointment()) {
            throw new BookingRuleViolationException("Slot này đã được đặt");
        }
    }

    @Override
    public int getOrder() {
        return 30;
    }
}
