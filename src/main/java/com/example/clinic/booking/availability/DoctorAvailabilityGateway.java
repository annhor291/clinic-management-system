package com.example.clinic.booking.availability;

import com.example.clinic.entity.TimeSlot;

// Trừu tượng hoá cách hệ thống truy vấn/khoá tài nguyên slot của bác sĩ.
// Đây là điểm mấu chốt để sau này chuyển sang Dynamic Slot (không còn TimeSlot cố định):
// chỉ cần viết implementation mới cho interface này, mọi Rule và Service phía trên không cần sửa.
public interface DoctorAvailabilityGateway {

    // Tìm slot theo id và khoá lại (pessimistic lock) — chống double-booking khi nhiều request cùng lúc
    TimeSlot lockSlot(Long timeSlotId);

    // Kiểm tra slot đã có appointment khác (chưa bị huỷ) gắn vào chưa
    boolean hasConflictingAppointment(Long timeSlotId);
}
