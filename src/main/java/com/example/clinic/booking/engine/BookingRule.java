package com.example.clinic.booking.engine;

// Đại diện cho 1 Business Rule độc lập trong quá trình đặt lịch.
// Mỗi implementation chỉ chịu trách nhiệm kiểm tra đúng 1 điều kiện.
// Spring sẽ tự động gom mọi bean implement interface này thành List<BookingRule>
// (Strategy Pattern qua danh sách bean — không cần Chain of Responsibility thủ công).
public interface BookingRule {

    // Kiểm tra rule; ném BookingRuleViolationException nếu vi phạm
    void validate(BookingContext context);

    // Thứ tự chạy — số nhỏ chạy trước.
    // Rule rẻ (so sánh thời gian đơn giản) nên chạy trước rule tốn kém (query DB nhiều bảng).
    default int getOrder() {
        return 0;
    }
}
