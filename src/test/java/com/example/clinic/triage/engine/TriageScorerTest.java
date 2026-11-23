package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TriageScorerTest {

    private static TriageScorer scorer;

    @BeforeAll
    static void init() {
        scorer = new TriageScorer(new TriageRuleBook());
    }

    private static Map<String, List<String>> ans(String... keyValuePairs) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            m.put(keyValuePairs[i], List.of(keyValuePairs[i + 1].split(",")));
        }
        return m;
    }

    @Test
    void matchingRule_givesItsWeights() {
        TriageScorer.ScoreCard card = scorer.score(ans(
                "AGE", "ADULT", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY", "Q_ONSET", "DAYS_1_3"));

        assertThat(card.scores()).containsEntry("INTERNAL", 5).containsEntry("RESP", 2);
        assertThat(card.strongest()).containsEntry("INTERNAL", 5);
        assertThat(card.matchedRuleIds()).containsExactly("SC_COUGH_SHORT");
    }

    @Test
    void severalRules_accumulate_butStrongestKeepsTheBiggestSingleWeight() {
        // Đau bụng trên (GI 5) + kiểu quặn thắt (GI +2) => điểm 7 nhưng luật đơn mạnh nhất vẫn là 5
        TriageScorer.ScoreCard card = scorer.score(ans(
                "AGE", "ADULT", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_UPPER", "Q_QUALITY", "CRAMPING"));

        assertThat(card.scores()).containsEntry("GI", 7);
        assertThat(card.strongest()).containsEntry("GI", 5);
    }

    @Test
    void adultRules_doNotApplyToChildren() {
        TriageScorer.ScoreCard card = scorer.score(ans(
                "AGE", "CHILD", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY", "Q_ONSET", "DAYS_1_3"));

        assertThat(card.scores()).containsOnlyKeys("PEDIATRIC");
    }

    @Test
    void noMatchingRule_givesEmptyCard() {
        TriageScorer.ScoreCard card = scorer.score(ans("AGE", "INFANT"));

        assertThat(card.scores()).isEmpty();
        assertThat(card.matchedRuleIds()).isEmpty();
    }
}
