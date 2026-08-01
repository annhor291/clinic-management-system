package com.example.clinic.booking.rules;

import com.example.clinic.booking.engine.BookingContext;
import com.example.clinic.booking.engine.BookingRule;
import com.example.clinic.booking.engine.BookingRuleViolationException;
import com.example.clinic.entity.enums.SlotStatus;
import org.springframework.stereotype.Component;

// Slot phải đang ở trạng thái AVAILABLE mới được đặt
@Component
public class SlotCapacityRule implements BookingRule {

    @Override
    public void validate(BookingContext context) {
        if (context.slotStatus() != SlotStatus.AVAILABLE) {
            throw new BookingRuleViolationException("Slot này đã được đặt hoặc không còn khả dụng");
        }
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
