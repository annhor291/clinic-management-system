package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.rules.Conditions;
import com.example.clinic.triage.rules.QuestionType;
import com.example.clinic.triage.rules.TriageRules;
import org.springframework.stereotype.Component;

import java.util.*;

// Kiểm tra câu trả lời client gửi lên: dữ liệu từ ngoài vào luôn bị coi là không đáng tin.
// Sai là IllegalStateException — GlobalExceptionHandler của dự án đổi thành HTTP 400.
@Component
public class TriageAnswerValidator {

    private final Map<String, TriageRules.Question> questions = new HashMap<>();

    public TriageAnswerValidator(TriageRuleBook book) {
        book.rules().questions().forEach(q -> questions.put(q.code(), q));
    }

    public void validate(Map<String, List<String>> answers) {
        if (answers == null) {
            throw invalid("Thiếu câu trả lời");
        }

        // Bước 1: từng câu trả lời phải đúng câu hỏi, đúng đáp án, đúng kiểu chọn
        answers.forEach((code, given) -> {
            TriageRules.Question q = questions.get(code);
            if (q == null) {
                throw invalid("Câu hỏi không tồn tại: " + code);
            }
            validateValues(q, given);
        });

        // Bước 2: câu hỏi phải thật sự được hỏi với các câu trả lời trước đó
        answers.keySet().forEach(code -> {
            if (!Conditions.matches(questions.get(code).showIf(), answers)) {
                throw invalid("Câu hỏi '" + code + "' không áp dụng với các câu trả lời trước đó");
            }
        });
    }

    private void validateValues(TriageRules.Question q, List<String> given) {
        if (given == null || given.isEmpty()) {
            throw invalid("Câu hỏi '" + q.code() + "' chưa có đáp án");
        }
        if (new HashSet<>(given).size() != given.size()) {
            throw invalid("Câu hỏi '" + q.code() + "' có đáp án bị lặp");
        }

        Set<String> valid = new HashSet<>();
        Set<String> exclusive = new HashSet<>();
        for (TriageRules.Option o : q.options()) {
            valid.add(o.code());
            if (o.exclusive()) exclusive.add(o.code());
        }
        for (String a : given) {
            if (!valid.contains(a)) {
                throw invalid("Đáp án '" + a + "' không thuộc câu hỏi '" + q.code() + "'");
            }
        }

        if (q.type() == QuestionType.SINGLE && given.size() != 1) {
            throw invalid("Câu hỏi '" + q.code() + "' chỉ được chọn một đáp án");
        }
        if (q.type() == QuestionType.MULTI && given.size() > 1 && given.stream().anyMatch(exclusive::contains)) {
            throw invalid("Câu hỏi '" + q.code() + "': đáp án \"không có\" không được chọn cùng đáp án khác");
        }
    }

    private static IllegalStateException invalid(String message) {
        return new IllegalStateException(message);
    }
}
