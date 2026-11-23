package com.example.clinic.booking.suggestion;

import com.example.clinic.entity.Appointment;
import com.example.clinic.entity.Doctor;
import com.example.clinic.entity.TimeSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class SlotSuggestionScorerTest {

    private static final LocalDate ORIGIN_DATE = LocalDate.of(2026, 11, 10);
    private static final LocalTime ORIGIN_TIME = LocalTime.of(9, 0);

    private final SlotSuggestionScorer scorer = new SlotSuggestionScorer();
    private final Doctor doctorA = Doctor.builder().id(1L).build();
    private final Doctor doctorB = Doctor.builder().id(2L).build();

    private TimeSlot slot(Doctor doctor, LocalDate date, LocalTime start) {
        return TimeSlot.builder().doctor(doctor).slotDate(date)
                .startTime(start).endTime(start.plusMinutes(30)).build();
    }

    // Lịch đã hủy: bác sĩ A, 09:00 ngày ORIGIN_DATE
    private Appointment cancelled() {
        return Appointment.builder().doctor(doctorA)
                .timeSlot(slot(doctorA, ORIGIN_DATE, ORIGIN_TIME)).build();
    }

    @Test
    void sameDoctorSameDateSameTime_shouldScoreMaxWithAllReasons() {
        SlotSuggestionScorer.Scored scored = scorer.score(slot(doctorA, ORIGIN_DATE, ORIGIN_TIME), cancelled(), ORIGIN_DATE);

        // 50 (cùng bác sĩ) + 15 (cùng thứ) + 30 (cùng giờ) - 0 ngày lệch
        assertThat(scored.score()).isEqualTo(95);
        assertThat(scored.reasons())
                .containsExactly("Cùng bác sĩ", "Cùng thứ trong tuần", "Cùng khung giờ");
    }

    // Khác bác sĩ, cùng ngày: điểm = 15 (cùng thứ) + điểm theo độ lệch giờ
    @ParameterizedTest
    @CsvSource({
            "0, 30", "30, 30", "31, 20", "60, 20", "90, 10", "120, 10", "121, 0", "300, 0"
    })
    void timeProximity_shouldFollowThresholds(int offsetMinutes, int expectedTimePoints) {
        SlotSuggestionScorer.Scored scored = scorer.score(
                slot(doctorB, ORIGIN_DATE, ORIGIN_TIME.plusMinutes(offsetMinutes)), cancelled(), ORIGIN_DATE);

        assertThat(scored.score()).isEqualTo(15 + expectedTimePoints);
    }

    @Test
    void dayDistance_shouldReduceScoreOnePerDay() {
        // Khác bác sĩ, lệch 1 ngày (khác thứ), cùng giờ: 0 + 0 + 30 - 1
        SlotSuggestionScorer.Scored scored = scorer.score(slot(doctorB, ORIGIN_DATE.plusDays(1), ORIGIN_TIME), cancelled(), ORIGIN_DATE);

        assertThat(scored.score()).isEqualTo(29);
    }

    @Test
    void dayDistance_shouldBeCappedAt14() {
        // Lệch 21 ngày (cùng thứ): 15 + 30 - 14 (không phải trừ 21)
        SlotSuggestionScorer.Scored scored = scorer.score(slot(doctorB, ORIGIN_DATE.plusDays(21), ORIGIN_TIME), cancelled(), ORIGIN_DATE);

        assertThat(scored.score()).isEqualTo(31);
    }

    @Test
    void ranking_shouldSortByScoreThenEarlierDateAndTime() {
        SlotSuggestionScorer.Scored best = scorer.score(slot(doctorA, ORIGIN_DATE, ORIGIN_TIME.plusMinutes(30)), cancelled(), ORIGIN_DATE);   // 95
        SlotSuggestionScorer.Scored tieLater = scorer.score(slot(doctorB, ORIGIN_DATE.plusDays(1), ORIGIN_TIME), cancelled(), ORIGIN_DATE);   // 29
        SlotSuggestionScorer.Scored tieEarlier = scorer.score(slot(doctorB, ORIGIN_DATE.plusDays(1), ORIGIN_TIME.minusMinutes(30)), cancelled(), ORIGIN_DATE); // 29

        List<SlotSuggestionScorer.Scored> sorted = List.of(tieLater, best, tieEarlier).stream()
                .sorted(scorer.ranking()).toList();

        assertThat(sorted).containsExactly(best, tieEarlier, tieLater);
    }
}
