package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.rules.TriageOutcome;
import com.example.clinic.triage.rules.TriageRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Gate chỉ nhìn phiếu điểm: test trực tiếp bằng phiếu điểm tự tạo, không cần câu trả lời nào.
public class TriageResultGateTest {

    private static TriageResultGate gate;

    @BeforeAll
    static void init() {
        TriageRuleBook book = new TriageRuleBook();
        gate = new TriageResultGate(book);

        // Các test dưới đây giả định ngưỡng trong file luật là 5 / 3 / 3
        TriageRules.Gate g = book.rules().gate();
        assertThat(g.minTopScore()).isEqualTo(5);
        assertThat(g.minMargin()).isEqualTo(3);
        assertThat(g.strongWeight()).isEqualTo(3);
    }

    private static TriageScorer.ScoreCard card(Map<String, Integer> scores, Map<String, Integer> strongest) {
        return new TriageScorer.ScoreCard(scores, strongest, List.of());
    }

    @Test
    void clearWinner_passes() {
        TriageResultGate.GateDecision d = gate.decide(card(Map.of("GI", 5), Map.of("GI", 5)));

        assertThat(d.outcome()).isEqualTo(TriageOutcome.PASS);
        assertThat(d.specialtyCode()).isEqualTo("GI");
        assertThat(d.reason()).isEqualTo("OK");
    }

    @Test
    void marginExactlyAtThreshold_passes() {
        TriageResultGate.GateDecision d = gate.decide(card(Map.of("GI", 6, "INTERNAL", 3), Map.of("GI", 5, "INTERNAL", 3)));

        assertThat(d.outcome()).isEqualTo(TriageOutcome.PASS);
    }

    @Test
    void noScore_isReferred() {
        TriageResultGate.GateDecision d = gate.decide(card(Map.of(), Map.of()));

        assertThat(d.outcome()).isEqualTo(TriageOutcome.REFER);
        assertThat(d.reason()).isEqualTo("NO_SCORE");
        assertThat(d.specialtyCode()).isNull();
    }

    @Test
    void topScoreTooLow_isReferred() {
        TriageResultGate.GateDecision d = gate.decide(card(Map.of("GI", 4), Map.of("GI", 4)));

        assertThat(d.outcome()).isEqualTo(TriageOutcome.REFER);
        assertThat(d.reason()).isEqualTo("LOW_SCORE");
    }

    @Test
    void marginTooSmall_isReferred() {
        TriageResultGate.GateDecision d = gate.decide(card(Map.of("GI", 6, "INTERNAL", 4), Map.of("GI", 5, "INTERNAL", 4)));

        assertThat(d.outcome()).isEqualTo(TriageOutcome.REFER);
        assertThat(d.reason()).isEqualTo("LOW_MARGIN");
    }

    @Test
    void manyWeakSignals_doNotAddUpToAPass() {
        // Điểm tổng 6 nhưng không luật đơn lẻ nào đạt trọng số "mạnh" (3): không được PASS
        TriageResultGate.GateDecision d = gate.decide(card(Map.of("GI", 6), Map.of("GI", 2)));

        assertThat(d.outcome()).isEqualTo(TriageOutcome.REFER);
        assertThat(d.reason()).isEqualTo("NO_STRONG_SIGNAL");
    }

    @Test
    void tie_isReferred_andRankingIsDeterministic() {
        TriageResultGate.GateDecision d = gate.decide(card(Map.of("MSK", 5, "GI", 5), Map.of("MSK", 5, "GI", 5)));

        assertThat(d.outcome()).isEqualTo(TriageOutcome.REFER);
        assertThat(d.reason()).isEqualTo("LOW_MARGIN");
        assertThat(d.ranking()).extracting(TriageResultGate.Ranked::specialtyCode)
                .containsExactly("GI", "MSK");
    }
}
