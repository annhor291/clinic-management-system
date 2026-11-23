package com.example.clinic.triage;

import com.example.clinic.triage.rules.Conditions;
import com.example.clinic.triage.rules.TriageOutcome;
import com.example.clinic.triage.rules.TriageRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

// Kiểm tra NỘI DUNG file luật thật (không phải cấu trúc — phần đó đã có TriageRuleLoaderTest).
public class TriageRulesContentTest {

    private static final int MAX_SCREENS = 10;

    private static TriageRules rules;

    @BeforeAll
    static void load() {
        rules = new TriageRuleBook().rules();
    }

    // ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_NONE") -> { AGE:[ADULT], Q_RED_FLAGS:[RF_NONE] }
    private static Map<String, List<String>> ans(String... keyValuePairs) {
        Map<String, List<String>> m = new HashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            m.put(keyValuePairs[i], List.of(keyValuePairs[i + 1]));
        }
        return m;
    }

    private static List<String> firedRuleIds(Map<String, List<String>> answers) {
        return rules.escalation().stream()
                .filter(r -> Conditions.matches(r.when(), answers))
                .map(TriageRules.EscalationRule::id)
                .toList();
    }

    @Test
    void everyRedFlag_exceptSelfHarm_firesEmergencyRule() {
        TriageRules.Question redFlags = rules.questions().stream()
                .filter(q -> q.code().equals("Q_RED_FLAGS")).findFirst().orElseThrow();

        for (TriageRules.Option o : redFlags.options()) {
            if (o.exclusive() || o.code().equals("RF_SELF_HARM")) continue;

            assertThat(firedRuleIds(ans("AGE", "ADULT", "Q_RED_FLAGS", o.code())))
                    .as("dấu hiệu %s phải ra EMERGENCY", o.code())
                    .containsExactly("ESC_RED_FLAGS");
        }
    }

    @Test
    void selfHarm_firesOnlyItsDedicatedRule() {
        assertThat(firedRuleIds(ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_SELF_HARM")))
                .containsExactly("ESC_SELF_HARM");
    }

    @Test
    void noRedFlag_firesNothing() {
        assertThat(firedRuleIds(ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_NONE"))).isEmpty();
    }

    @Test
    void emergencyRules_dependOnlyOnRedFlagQuestion() {
        // EMERGENCY chỉ được đến từ câu dấu hiệu nguy hiểm — không từ tổ hợp suy diễn khác
        Set<String> keys = new HashSet<>();
        rules.escalation().stream()
                .filter(r -> r.outcome() == TriageOutcome.EMERGENCY)
                .forEach(r -> keys.addAll(r.when().keySet()));

        assertThat(keys).containsExactly("Q_RED_FLAGS");
    }

    @Test
    void infant_isReferredImmediately() {
        assertThat(firedRuleIds(ans("AGE", "INFANT"))).containsExactly("ESC_INFANT");
    }

    @Test
    void everyEscalationRule_isReachable_andNoPathExceedsMaxScreens() {
        Set<String> fired = new HashSet<>();
        int[] maxScreens = {0};

        walk(new HashMap<>(), 0, fired, maxScreens);

        Set<String> neverFired = new HashSet<>();
        rules.escalation().forEach(r -> neverFired.add(r.id()));
        neverFired.removeAll(fired);

        assertThat(neverFired).as("luật không bao giờ kích hoạt được (luật chết)").isEmpty();
        assertThat(maxScreens[0]).isLessThanOrEqualTo(MAX_SCREENS);
    }

    // Duyệt mọi đường đi (mỗi câu thử từng đáp án đơn lẻ). Dừng khi có luật leo thang kích hoạt
    // hoặc khi không còn câu hỏi nào để hỏi.
    private static void walk(Map<String, List<String>> answers, int depth, Set<String> fired, int[] maxScreens) {
        List<TriageRules.EscalationRule> hits = rules.escalation().stream()
                .filter(r -> Conditions.matches(r.when(), answers))
                .toList();
        if (!hits.isEmpty()) {
            hits.forEach(r -> fired.add(r.id()));
            return;
        }

        TriageRules.Question next = rules.questions().stream()
                .filter(q -> !answers.containsKey(q.code()) && Conditions.matches(q.showIf(), answers))
                .findFirst()
                .orElse(null);
        if (next == null) {
            maxScreens[0] = Math.max(maxScreens[0], depth);
            return;
        }

        for (TriageRules.Option o : next.options()) {
            Map<String, List<String>> copy = new HashMap<>(answers);
            copy.put(next.code(), List.of(o.code()));
            walk(copy, depth + 1, fired, maxScreens);
        }
    }

    @Test
    void everyScoringRule_isScopedByAge() {
        // Luật người lớn không được "rò" sang trẻ em (và ngược lại)
        assertThat(rules.scoring())
                .allSatisfy(r -> assertThat(r.when()).containsKey("AGE"));
    }

    @Test
    void everySpecialty_hasAtLeastOneStrongScoringRule() {
        int strong = rules.gate().strongWeight();
        for (String code : rules.specialties().keySet()) {
            assertThat(rules.scoring().stream()
                    .anyMatch(r -> r.weights().getOrDefault(code, 0) >= strong))
                    .as("chuyên khoa %s phải có ít nhất một luật đủ mạnh để PASS", code)
                    .isTrue();
        }
    }
}
