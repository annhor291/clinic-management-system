package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.rules.Conditions;
import com.example.clinic.triage.rules.TriageRules;
import org.springframework.stereotype.Component;

import java.util.*;

// Cộng điểm chuyên khoa theo các luật khớp. Không quyết định gì cả — chỉ đưa "phiếu điểm" cho người duyệt.
@Component
public class TriageScorer {

    // scores: tổng điểm mỗi chuyên khoa
    // strongest: trọng số lớn nhất mà MỘT luật đơn lẻ đã cho chuyên khoa đó (để biết có tín hiệu mạnh hay chỉ cộng dồn tín hiệu yếu)
    // matchedRuleIds: các luật đã khớp (để giải thích kết quả)
    public record ScoreCard(Map<String, Integer> scores,
                            Map<String, Integer> strongest,
                            List<String> matchedRuleIds) {}

    private final List<TriageRules.ScoringRule> rules;

    public TriageScorer(TriageRuleBook book) {
        this.rules = book.rules().scoring();
    }

    public ScoreCard score(Map<String, List<String>> answers) {
        Map<String, Integer> scores = new TreeMap<>();
        Map<String, Integer> strongest = new TreeMap<>();
        List<String> matched = new ArrayList<>();

        for (TriageRules.ScoringRule rule : rules) {
            if (!Conditions.matches(rule.when(), answers)) continue;
            matched.add(rule.id());
            rule.weights().forEach((specialty, weight) -> {
                scores.merge(specialty, weight, Integer::sum);
                strongest.merge(specialty, weight, Math::max);
            });
        }

        return new ScoreCard(
                Collections.unmodifiableMap(scores),
                Collections.unmodifiableMap(strongest),
                List.copyOf(matched));
    }
}
