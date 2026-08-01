package com.example.clinic.booking.scheduler;

import com.example.clinic.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

// Định kỳ quét các lịch hẹn PENDING quá hạn xác nhận và chuyển sang EXPIRED,
// đồng thời giải phóng slot về AVAILABLE.
// Xử lý từng appointment trong 1 transaction riêng (qua appointmentService.expireOne)
// để 1 lỗi/conflict không làm rollback toàn bộ batch.
@Component
@RequiredArgsConstructor
@Slf4j
public class AppointmentExpiryScheduler {

    private final AppointmentService appointmentService;

    @Scheduled(fixedDelayString = "${booking.pending-expiry-check-interval-ms:60000}")
    public void expirePendingAppointments() {
        List<Long> expirableIds = appointmentService.findExpirablePendingIds();

        for (Long id : expirableIds) {
            try {
                appointmentService.expireOne(id);
            } catch (OptimisticLockingFailureException e) {
                // Lịch hẹn vừa bị thay đổi bởi hành động khác (VD: receptionist confirm cùng lúc) — bỏ qua, không phải lỗi
                log.debug("Bỏ qua expire appointment id={} do bị thay đổi đồng thời", id);
            } catch (Exception e) {
                log.error("Lỗi khi expire appointment id={}", id, e);
            }
        }
    }
}
