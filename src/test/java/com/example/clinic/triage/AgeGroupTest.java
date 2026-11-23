package com.example.clinic.triage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AgeGroupTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    // Kiểm tra sát ranh giới: hôm trước và đúng ngày sinh nhật
    @ParameterizedTest(name = "sinh {0} → {1}")
    @CsvSource({
            "2026-11-17, INFANT",
            "2025-11-18, INFANT",
            "2025-11-17, CHILD",
            "2011-11-18, CHILD",
            "2011-11-17, ADULT",
            "1966-11-18, ADULT",
            "1966-11-17, SENIOR",
            "1896-11-17, SENIOR"
    })
    void boundaries(String dateOfBirth, String expected) {
        assertThat(AgeGroup.of(LocalDate.parse(dateOfBirth), TODAY)).contains(expected);
    }

    @Test
    void missingOrImplausibleDateOfBirth_givesNothing() {
        assertThat(AgeGroup.of(null, TODAY)).isEmpty();
        assertThat(AgeGroup.of(TODAY.plusDays(1), TODAY)).isEmpty();      // ngày sinh ở tương lai
        assertThat(AgeGroup.of(LocalDate.of(1890, 1, 1), TODAY)).isEmpty(); // hơn 130 tuổi: nhập sai
    }

    // Mã nhóm tuổi phải đúng là các đáp án của câu AGE trong bộ luật — lệch là nhóm tuổi sẽ bị từ chối
    @Test
    void everyAgeGroupIsAnOptionOfTheAgeQuestion() {
        List<String> options = new TriageRuleBook().rules().questions().get(0).options().stream()
                .map(o -> o.code()).toList();

        assertThat(options).containsExactlyInAnyOrder("INFANT", "CHILD", "ADULT", "SENIOR");
        for (String dob : List.of("2026-11-17", "2020-01-01", "1990-01-01", "1950-01-01")) {
            assertThat(options).contains(AgeGroup.of(LocalDate.parse(dob), TODAY).orElseThrow());
        }
    }
}
