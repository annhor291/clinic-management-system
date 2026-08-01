package com.example.clinic.booking.rules;

import com.example.clinic.booking.engine.BookingContext;
import com.example.clinic.booking.engine.BookingRule;
import com.example.clinic.booking.engine.BookingRuleViolationException;
import org.springframework.stereotype.Component;

// Khoảng đệm tối thiểu giữa 2 lịch hẹn của cùng 1 bệnh nhân (config qua BookingProperties)
@Component
public class BufferTimeRule implements BookingRule {

    @Override
    public void validate(BookingContext context) {
        if (context.hasBufferViolation()) {
            throw new BookingRuleViolationException(
                    "Lịch hẹn này quá gần với lịch hẹn khác của bạn, vui lòng chọn khung giờ khác");
        }
    }

    @Override
    public int getOrder() {
        return 50;
    }
}
