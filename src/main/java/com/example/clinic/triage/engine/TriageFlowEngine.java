package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.rules.Conditions;
import com.example.clinic.triage.rules.TriageOutcome;
import com.example.clinic.triage.rules.TriageRules;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// Bộ điều phối thuần logic (không đụng DB, không lưu gì): nhận các câu trả lời hiện có và trả về BƯỚC TIẾP THEO.
//
// Thứ tự quyết định (không thể đảo):
//   1. Kiểm tra câu trả lời hợp lệ
//   2. Luật leo thang (EMERGENCY / REFER) — chạy ngay sau MỖI câu trả lời, không đợi hỏi hết
//   3. Còn câu hỏi chưa trả lời thì hỏi tiếp
//   4. Hết câu hỏi mới chấm điểm → Gate quyết định PASS / REFER
//
// An toàn: PASS chỉ có thể ra ở bước 4, tức là khi MỌI câu hỏi hiện ra đã được trả lời
// (không thể bỏ qua câu dấu hiệu nguy hiểm) và không có luật leo thang nào khớp.
@Component
public class TriageFlowEngine {

    public enum Status { QUESTION, EMERGENCY, REFER, PASS }

    // question: có khi status = QUESTION
    // escalations: có khi EMERGENCY hoặc REFER do luật leo thang
    // gate: có khi PASS hoặc REFER do Gate (không đủ chắc chắn)
    public record TriageStep(Status status,
                             TriageRules.Question question,
                             List<TriageRules.EscalationRule> escalations,
                             TriageResultGate.GateDecision gate) {

        static TriageStep ask(TriageRules.Question q) {
            return new TriageStep(Status.QUESTION, q, List.of(), null);
        }

        static TriageStep escalated(EscalationEvaluator.Escalation e) {
            Status s = e.outcome() == TriageOutcome.EMERGENCY ? Status.EMERGENCY : Status.REFER;
            return new TriageStep(s, null, e.rules(), null);
        }

        static TriageStep decided(TriageResultGate.GateDecision d) {
            Status s = d.outcome() == TriageOutcome.PASS ? Status.PASS : Status.REFER;
            return new TriageStep(s, null, List.of(), d);
        }
    }

    private final List<TriageRules.Question> questions;
    private final TriageAnswerValidator validator;
    private final EscalationEvaluator escalationEvaluator;
    private final TriageScorer scorer;
    private final TriageResultGate gate;

    public TriageFlowEngine(TriageRuleBook book,
                            TriageAnswerValidator validator,
                            EscalationEvaluator escalationEvaluator,
                            TriageScorer scorer,
                            TriageResultGate gate) {
        this.questions = book.rules().questions();
        this.validator = validator;
        this.escalationEvaluator = escalationEvaluator;
        this.scorer = scorer;
        this.gate = gate;
    }

    public TriageStep next(Map<String, List<String>> answers) {
        validator.validate(answers);

        var escalation = escalationEvaluator.evaluate(answers);
        if (escalation.isPresent()) {
            return TriageStep.escalated(escalation.get());
        }

        TriageRules.Question next = nextQuestion(answers);
        if (next != null) {
            return TriageStep.ask(next);
        }

        return TriageStep.decided(gate.decide(scorer.score(answers)));
    }

    // Câu đầu tiên (theo thứ tự khai báo) chưa được trả lời và đang hiện ra với các câu trả lời hiện có.
    // Nhờ luôn lấy câu CHƯA TRẢ LỜI SỚM NHẤT, client không thể nhảy cóc qua câu dấu hiệu nguy hiểm.
    private TriageRules.Question nextQuestion(Map<String, List<String>> answers) {
        return questions.stream()
                .filter(q -> !answers.containsKey(q.code()) && Conditions.matches(q.showIf(), answers))
                .findFirst()
                .orElse(null);
    }
}
