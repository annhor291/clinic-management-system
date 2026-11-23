package com.example.clinic.triage.rules;

import java.util.List;
import java.util.Map;

// Toàn bộ bộ luật gợi ý chuyên khoa — đọc từ triage/triage-rules.json.
// "when" / "showIf" có dạng { mã câu hỏi: [các đáp án chấp nhận] }:
// MỌI điều kiện phải thoả, mỗi điều kiện thoả khi người dùng chọn ÍT NHẤT MỘT đáp án trong danh sách.
public record TriageRules (
        int version,
        Gate gate,
        Map<String, String> specialties,          // mã chuyên khoa -> tên chuyên khoa trong DB
        List<Question> questions,                  // theo thứ tự hỏi; showIf chỉ được tham chiếu câu đứng trước
        List<EscalationRule> escalation,           // luật cứng, ưu tiên cao nhất
        List<ScoringRule> scoring                  // luật cộng điểm chuyên khoa
) {
    public TriageRules {
        specialties = specialties == null ? Map.of() : Map.copyOf(specialties);
        questions = questions == null ? List.of() : List.copyOf(questions);
        escalation = escalation == null ? List.of() : List.copyOf(escalation);
        scoring = scoring == null ? List.of() : List.copyOf(scoring);
    }

    // Ngưỡng của "người duyệt"
    public record Gate(int minTopScore, int minMargin, int strongWeight) {}

    public record Option(String code, String label, boolean exclusive) {}

    public record Question(
            String code,
            String text,
            QuestionType type,
            List<Option> options,
            Map<String, List<String>> showIf
    ) {
        public Question {
            options = options == null ? List.of() : List.copyOf(options);
            showIf = showIf == null ? Map.of() : Map.copyOf(showIf);
        }
    }

    public record EscalationRule(
            String id,
            Map<String, List<String>> when,
            TriageOutcome outcome,
            String reason
    ) {
        public EscalationRule {
            when = when == null ? Map.of() : Map.copyOf(when);
        }
    }

    public record ScoringRule(
            String id,
            Map<String, List<String>> when,
            Map<String, Integer> weights
    ) {
        public ScoringRule {
            when = when == null ? Map.of() : Map.copyOf(when);
            weights = weights == null ? Map.of() : Map.copyOf(weights);
        }
    }
}

