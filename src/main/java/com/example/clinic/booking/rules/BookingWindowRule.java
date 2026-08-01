package com.example.clinic.booking.rules;

import com.example.clinic.booking.config.BookingProperties;
import com.example.clinic.booking.engine.BookingContext;
import com.example.clinic.booking.engine.BookingRule;
import com.example.clinic.booking.engine.BookingRuleViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// Phải đặt lịch trước giờ hẹn tối thiểu 1 khoảng thời gian (config qua BookingProperties).
// Việc này tự động bao luôn cả trường hợp đặt cho quá khứ (vì now + minLeadTime luôn > now).
@Component
@RequiredArgsConstructor
public class BookingWindowRule implements BookingRule {

    private final BookingProperties bookingProperties;

    @Override
    public void validate(BookingContext context) {
        LocalDateTime earliestAllowed = context.now().plusMinutes(bookingProperties.getMinLeadTimeMinutes());
        if (context.startDateTime().isBefore(earliestAllowed)) {
            throw new BookingRuleViolationException(
                    "Phải đặt lịch trước ít nhất " + bookingProperties.getMinLeadTimeMinutes() + " phút so với giờ hẹn");
        }
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
