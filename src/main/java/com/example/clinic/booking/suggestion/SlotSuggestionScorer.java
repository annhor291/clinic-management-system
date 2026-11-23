package com.example.clinic.booking.suggestion;

import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.TimeSlot;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Chấm điểm slot ứng viên so với lịch đã hủy. Luật cố định, giải thích được:
//   cùng bác sĩ +50 | cùng thứ trong tuần +15 | giờ lệch ≤30' +30, ≤60' +20, ≤120' +10
//   trừ 1 điểm cho mỗi ngày lệch so với ngày gốc (tối đa 14)
@Component
public class SlotSuggestionScorer {

    public record Scored(TimeSlot slot, int score, List<String> reasons) {}

    public Scored score(TimeSlot candidate, Appointment cancelled, LocalDate baseDate) {
        TimeSlot origin = cancelled.getTimeSlot();
        int score = 0;
        List<String> reasons = new ArrayList<>();

        if (candidate.getDoctor().getId().equals(cancelled.getDoctor().getId())) {
            score += 50;
            reasons.add("Cùng bác sĩ");
        }
        if (candidate.getSlotDate().getDayOfWeek() == origin.getSlotDate().getDayOfWeek()) {
            score += 15;
            reasons.add("Cùng thứ trong tuần");
        }

        long diffMinutes = Math.abs(Duration.between(origin.getStartTime(), candidate.getStartTime()).toMinutes());
        if (diffMinutes <= 30) {
            score += 30;
            reasons.add("Cùng khung giờ");
        } else if (diffMinutes <= 60) {
            score += 20;
            reasons.add("Khung giờ gần nhau (lệch tối đa 1 giờ)");
        } else if (diffMinutes <= 120) {
            score += 10;
            reasons.add("Khung giờ gần nhau (lệch tối đa 2 giờ)");
        }

        long dayDistance = Math.abs(ChronoUnit.DAYS.between(baseDate, candidate.getSlotDate()));
        score -= (int) Math.min(dayDistance, 14);

        return new Scored(candidate, score, reasons);
    }

    // Điểm cao trước; bằng điểm thì ngày sớm hơn, giờ sớm hơn
    public Comparator<Scored> ranking() {
        return Comparator.comparingInt(Scored::score).reversed()
                .thenComparing(s -> s.slot().getSlotDate())
                .thenComparing(s -> s.slot().getStartTime());
    }
}
