package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.rules.Conditions;
import com.example.clinic.triage.rules.TriageOutcome;
import com.example.clinic.triage.rules.TriageRules;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

// Luật cứng: dấu hiệu nguy hiểm (EMERGENCY) và các điều kiện phải chuyển người thật (REFER).
// Chạy TRƯỚC chấm điểm và không bị chấm điểm ghi đè.
@Component
public class EscalationEvaluator {

    // outcome: kết quả nặng nhất trong các luật khớp; rules: MỌI luật khớp thuộc kết quả đó
    // (để biết cả "dấu hiệu nguy hiểm" lẫn "ý nghĩ tự làm hại" khi người dùng chọn cùng lúc)
    public record Escalation(TriageOutcome outcome, List<TriageRules.EscalationRule> rules) {}

    private final List<TriageRules.EscalationRule> rules;

    public EscalationEvaluator(TriageRuleBook book) {
        this.rules = book.rules().escalation();
    }

    public Optional<Escalation> evaluate(Map<String, List<String>> answers) {
        List<TriageRules.EscalationRule> matched = rules.stream()
                .filter(r -> Conditions.matches(r.when(), answers))
                .toList();
        if (matched.isEmpty()) {
            return Optional.empty();
        }

        TriageOutcome worst = matched.stream().anyMatch(r -> r.outcome() == TriageOutcome.EMERGENCY)
                ? TriageOutcome.EMERGENCY
                : TriageOutcome.REFER;

        return Optional.of(new Escalation(worst,
                matched.stream().filter(r -> r.outcome() == worst).toList()));
    }
}
