package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TriageAnswerValidatorTest {

    private static TriageAnswerValidator validator;

    @BeforeAll
    static void init() {
        validator = new TriageAnswerValidator(new TriageRuleBook());
    }

    // ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_CHEST,RF_BREATH") — nhiều đáp án cách nhau bằng dấu phẩy
    private static Map<String, List<String>> ans(String... keyValuePairs) {
        Map<String, List<String>> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            m.put(keyValuePairs[i], List.of(keyValuePairs[i + 1].split(",")));
        }
        return m;
    }

    @Test
    void validAnswers_pass() {
        assertThatCode(() -> validator.validate(
                ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_CHEST,RF_BREATH", "Q_COMPLAINT", "COUGH")))
                .doesNotThrowAnyException();
    }

    @Test
    void nullAnswers_areRejected() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Thiếu câu trả lời");
    }

    @Test
    void unknownQuestion_isRejected() {
        assertThatThrownBy(() -> validator.validate(ans("Q_KHONG_CO", "A")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không tồn tại");
    }

    @Test
    void unknownOption_isRejected() {
        assertThatThrownBy(() -> validator.validate(ans("AGE", "KHONG_CO")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không thuộc câu hỏi");
    }

    @Test
    void emptyAnswerList_isRejected() {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("AGE", List.of());

        assertThatThrownBy(() -> validator.validate(answers))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chưa có đáp án");
    }

    @Test
    void singleChoiceWithTwoAnswers_isRejected() {
        assertThatThrownBy(() -> validator.validate(ans("AGE", "ADULT,SENIOR")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("chỉ được chọn một");
    }

    @Test
    void duplicateAnswers_areRejected() {
        assertThatThrownBy(() -> validator.validate(ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_CHEST,RF_CHEST")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bị lặp");
    }

    @Test
    void noneOption_cannotBeCombinedWithOthers() {
        assertThatThrownBy(() -> validator.validate(ans("AGE", "ADULT", "Q_RED_FLAGS", "RF_NONE,RF_CHEST")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không được chọn cùng");
    }

    @Test
    void questionThatWasNeverAsked_isRejected() {
        // Hỏi vị trí đau trong khi khiếu nại là nhức đầu
        assertThatThrownBy(() -> validator.validate(
                ans("AGE", "ADULT", "Q_COMPLAINT", "HEADACHE", "Q_PAIN_AREA", "CHEST")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không áp dụng");
    }
}
