package com.example.clinic.triage.rules;

import java.util.*;
import java.util.regex.Pattern;

// Kiểm tra bộ luật lúc khởi động: sai là app KHÔNG chạy, để lỗi luật lộ ngay thay vì lộ khi bệnh nhân dùng.
public final class TriageRulesValidator {

    public static final String AGE = "AGE";
    public static final Set<String> AGE_OPTIONS = Set.of("INFANT", "CHILD", "ADULT", "SENIOR");
    public static final int MAX_WEIGHT = 5;

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]*");

    private TriageRulesValidator() {}

    public static void validate(TriageRules rules) {
        List<String> errors = new ArrayList<>();

        validateGate(rules.gate(), errors);
        validateSpecialties(rules.specialties(), errors);
        Map<String, TriageRules.Question> questions = validateQuestions(rules.questions(), errors);
        validateRules(rules, questions, errors);

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Bộ luật triage không hợp lệ:\n - " + String.join("\n - ", errors));
        }
    }

    private static void validateGate(TriageRules.Gate gate, List<String> errors) {
        if (gate == null) {
            errors.add("Thiếu 'gate'");
            return;
        }
        if (gate.minTopScore() < 1) errors.add("gate.minTopScore phải >= 1");
        if (gate.minMargin() < 0) errors.add("gate.minMargin phải >= 0");
        if (gate.strongWeight() < 1 || gate.strongWeight() > MAX_WEIGHT) {
            errors.add("gate.strongWeight phải trong khoảng 1.." + MAX_WEIGHT);
        }
    }

    private static void validateSpecialties(Map<String, String> specialties, List<String> errors) {
        if (specialties.isEmpty()) {
            errors.add("Thiếu 'specialties'");
        }
        specialties.forEach((code, name) -> {
            if (!CODE.matcher(code).matches()) errors.add("Mã chuyên khoa không hợp lệ: " + code);
            if (name == null || name.isBlank()) errors.add("Chuyên khoa " + code + " thiếu tên");
        });
    }

    // Trả về map mã câu hỏi -> câu hỏi (giữ thứ tự khai báo)
    private static Map<String, TriageRules.Question> validateQuestions(List<TriageRules.Question> list, List<String> errors) {
        Map<String, TriageRules.Question> seen = new LinkedHashMap<>();

        if (list.isEmpty()) {
            errors.add("Thiếu 'questions'");
            return seen;
        }

        for (int i = 0; i < list.size(); i++) {
            TriageRules.Question q = list.get(i);
            String where = "Câu hỏi #" + (i + 1) + " (" + q.code() + ")";

            if (q.code() == null || !CODE.matcher(q.code()).matches()) {
                errors.add(where + ": mã không hợp lệ");
                continue;
            }
            if (seen.containsKey(q.code())) {
                errors.add(where + ": trùng mã câu hỏi");
                continue;
            }
            if (q.text() == null || q.text().isBlank()) errors.add(where + ": thiếu nội dung câu hỏi");
            if (q.type() == null) errors.add(where + ": thiếu 'type'");
            validateOptions(q, where, errors);
            validateShowIf(q, where, seen, errors);

            seen.put(q.code(), q);
        }

        validateAgeQuestion(list.get(0), errors);
        return seen;
    }

    private static void validateOptions(TriageRules.Question q, String where, List<String> errors) {
        if (q.options().isEmpty()) {
            errors.add(where + ": không có đáp án nào");
            return;
        }
        Set<String> codes = new HashSet<>();
        boolean hasExclusive = false;
        for (TriageRules.Option o : q.options()) {
            if (o.code() == null || !CODE.matcher(o.code()).matches()) {
                errors.add(where + ": mã đáp án không hợp lệ: " + o.code());
                continue;
            }
            if (!codes.add(o.code())) errors.add(where + ": trùng mã đáp án " + o.code());
            if (o.label() == null || o.label().isBlank()) errors.add(where + ": đáp án " + o.code() + " thiếu nhãn");
            if (o.exclusive()) {
                hasExclusive = true;
                if (q.type() == QuestionType.SINGLE) {
                    errors.add(where + ": đáp án 'exclusive' chỉ dùng cho câu MULTI (" + o.code() + ")");
                }
            }
        }
        // Câu MULTI phải cho người dùng một cách trả lời "không có gì trong số này" bằng một lần chạm
        if (q.type() == QuestionType.MULTI && !hasExclusive) {
            errors.add(where + ": câu MULTI phải có đáp án 'exclusive' (kiểu \"Không có\")");
        }
    }

    private static void validateShowIf(TriageRules.Question q, String where, Map<String, TriageRules.Question> earlier, List<String> errors) {
        q.showIf().forEach((refCode, allowed) -> {
            TriageRules.Question ref = earlier.get(refCode);
            if (ref == null) {
                errors.add(where + ": showIf tham chiếu câu '" + refCode + "' không tồn tại hoặc đứng SAU câu này");
                return;
            }
            checkOptionsExist(where + " showIf", ref, allowed, errors);
        });
    }

    private static void validateAgeQuestion(TriageRules.Question first, List<String> errors) {
        if (!AGE.equals(first.code())) {
            errors.add("Câu hỏi đầu tiên phải là '" + AGE + "'");
            return;
        }
        Set<String> codes = new HashSet<>();
        first.options().forEach(o -> codes.add(o.code()));
        if (first.type() != QuestionType.SINGLE || !codes.equals(AGE_OPTIONS) || !first.showIf().isEmpty()) {
            errors.add("Câu '" + AGE + "' phải là SINGLE, không có showIf, đúng các đáp án " + AGE_OPTIONS);
        }
    }

    private static void validateRules(TriageRules rules, Map<String, TriageRules.Question> questions, List<String> errors) {
        Set<String> ids = new HashSet<>();

        for (TriageRules.EscalationRule r : rules.escalation()) {
            String where = "Luật leo thang " + r.id();
            checkId(r.id(), ids, where, errors);
            checkWhen(r.when(), questions, where, errors);
            if (r.outcome() != TriageOutcome.EMERGENCY && r.outcome() != TriageOutcome.REFER) {
                errors.add(where + ": outcome chỉ được là EMERGENCY hoặc REFER");
            }
            if (r.reason() == null || r.reason().isBlank()) errors.add(where + ": thiếu 'reason'");
        }

        for (TriageRules.ScoringRule r : rules.scoring()) {
            String where = "Luật chấm điểm " + r.id();
            checkId(r.id(), ids, where, errors);
            checkWhen(r.when(), questions, where, errors);
            if (r.weights().isEmpty()) errors.add(where + ": thiếu 'weights'");
            r.weights().forEach((spec, w) -> {
                if (!rules.specialties().containsKey(spec)) {
                    errors.add(where + ": chuyên khoa '" + spec + "' không có trong 'specialties'");
                }
                if (w == null || w < 1 || w > MAX_WEIGHT) {
                    errors.add(where + ": trọng số của " + spec + " phải trong khoảng 1.." + MAX_WEIGHT);
                }
            });
        }
    }

    private static void checkId(String id, Set<String> ids, String where, List<String> errors) {
        if (id == null || id.isBlank()) {
            errors.add(where + ": thiếu 'id'");
        } else if (!ids.add(id)) {
            errors.add(where + ": trùng id");
        }
    }

    private static void checkWhen(Map<String, List<String>> when, Map<String, TriageRules.Question> questions,
                                  String where, List<String> errors) {
        if (when.isEmpty()) {
            errors.add(where + ": 'when' không được rỗng (luật sẽ áp dụng cho mọi người)");
            return;
        }
        when.forEach((refCode, allowed) -> {
            TriageRules.Question ref = questions.get(refCode);
            if (ref == null) {
                errors.add(where + ": 'when' tham chiếu câu '" + refCode + "' không tồn tại");
                return;
            }
            checkOptionsExist(where + " when", ref, allowed, errors);
        });
    }

    private static void checkOptionsExist(String where, TriageRules.Question ref, List<String> allowed, List<String> errors) {
        if (allowed == null || allowed.isEmpty()) {
            errors.add(where + ": danh sách đáp án của '" + ref.code() + "' đang rỗng");
            return;
        }
        Set<String> valid = new HashSet<>();
        ref.options().forEach(o -> valid.add(o.code()));
        for (String a : allowed) {
            if (!valid.contains(a)) errors.add(where + ": '" + a + "' không phải đáp án của câu " + ref.code());
        }
    }
}
