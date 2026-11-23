package com.example.clinic.service;

import java.time.LocalDate;

public interface DoctorBlockService {

    // Chặn bác sĩ nhận bệnh nhân trong [from, to] (to = null: vô thời hạn):
    // hủy mọi lịch hẹn còn ở tương lai (kèm thông báo, hoàn tiền qua sự kiện sẵn có),
    // khóa slot và vô hiệu hóa các ca làm việc. Trả về số lịch hẹn đã hủy.
    int blockDoctor(Long doctorId, LocalDate from, LocalDate to, String reason);
}
