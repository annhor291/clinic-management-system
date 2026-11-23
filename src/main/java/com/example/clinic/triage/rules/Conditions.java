package com.example.clinic.triage.rules;

import java.util.List;
import java.util.Map;

// Cách duy nhất để đánh giá "when" / "showIf" — dùng chung cho mọi thành phần để không lệch nhau.
public final class Conditions {

    private Conditions() {}

    // true nếu MỌI điều kiện đều có ít nhất một đáp án trùng với câu trả lời của người dùng.
    // Điều kiện rỗng luôn đúng (câu hỏi không có showIf thì luôn hiện).
    public static boolean matches(Map<String, List<String>> condition, Map<String, List<String>> answers) {
        for (Map.Entry<String, List<String>> c : condition.entrySet()) {
            List<String> given = answers.get(c.getKey());
            if (given == null || given.stream().noneMatch(c.getValue()::contains)) {
                return false;
            }
        }
        return true;
    }
}
