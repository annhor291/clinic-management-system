package com.example.clinic.booking.engine;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

// Chạy tuần tự toàn bộ BookingRule đã đăng ký theo thứ tự getOrder() tăng dần.
// Dừng ngay khi gặp rule đầu tiên vi phạm (fail-fast).
// Danh sách "rules" hiện đang RỖNG vì chưa có class nào implement BookingRule —
// sẽ được lấp đầy dần ở Bước 2 và Bước 3.
@Component
@RequiredArgsConstructor
public class BookingRuleEngine {

    private final List<BookingRule> rules;

    public void validate(BookingContext context) {
        rules.stream()
                .sorted(Comparator.comparingInt(BookingRule::getOrder))
                .forEach(rule -> rule.validate(context));
    }
}
