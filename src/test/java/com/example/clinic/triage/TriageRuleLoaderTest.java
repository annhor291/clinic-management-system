package com.example.clinic.triage;

import com.example.clinic.triage.rules.TriageRuleLoader;
import com.example.clinic.triage.rules.TriageRules;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Unit test thuần, không khởi động Spring.
public class TriageRuleLoaderTest {

    private static final String VALID = """
            {
              "version": 1,
              "gate": { "minTopScore": 5, "minMargin": 3, "strongWeight": 3 },
              "specialties": { "GI": "Tiêu hóa" },
              "questions": [
                { "code": "AGE", "text": "Tuổi?", "type": "SINGLE", "options": [
                  { "code": "INFANT", "label": "a" }, { "code": "CHILD", "label": "b" },
                  { "code": "ADULT", "label": "c" }, { "code": "SENIOR", "label": "d" } ] },
                { "code": "Q_RF", "text": "Dấu hiệu?", "type": "MULTI", "options": [
                  { "code": "RF_CHEST", "label": "Đau ngực" },
                  { "code": "RF_NONE", "label": "Không có", "exclusive": true } ] },
                { "code": "Q_AREA", "text": "Vùng?", "type": "SINGLE", "options": [
                  { "code": "BELLY", "label": "Bụng" } ] }
              ],
              "escalation": [
                { "id": "ESC_RF", "when": { "Q_RF": ["RF_CHEST"] }, "outcome": "EMERGENCY", "reason": "x" }
              ],
              "scoring": [
                { "id": "SC_BELLY", "when": { "Q_AREA": ["BELLY"] }, "weights": { "GI": 5 } }
              ]
            }
            """;

    @Test
    void validRules_load() {
        TriageRules rules = TriageRuleLoader.parse(VALID);

        assertThat(rules.questions()).hasSize(3);
        assertThat(rules.gate().minTopScore()).isEqualTo(5);
        assertThat(rules.scoring().get(0).weights()).containsEntry("GI", 5);
    }

    @Test
    void starterFile_inResources_isValid() {
        // File thật trong src/main/resources: sai là app không khởi động được
        TriageRules rules = new TriageRuleBook().rules();

        assertThat(rules.questions().get(0).code()).isEqualTo("AGE");
    }

    @Test
    void unknownProperty_isRejected() {
        assertThatThrownBy(() -> TriageRuleLoader.parse(VALID.replace("\"outcome\"", "\"outcom\"")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void firstQuestion_mustBeAge() {
        String json = VALID.replace("\"code\": \"AGE\"", "\"code\": \"AGE_X\"");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("đầu tiên phải là 'AGE'");
    }

    @Test
    void duplicateQuestionCode_isRejected() {
        String json = VALID.replace("\"code\": \"Q_AREA\"", "\"code\": \"Q_RF\"");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trùng mã câu hỏi");
    }

    @Test
    void multiQuestion_withoutExclusiveOption_isRejected() {
        String json = VALID.replace(", \"exclusive\": true", "");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exclusive");
    }

    @Test
    void ruleReferencingUnknownOption_isRejected() {
        String json = VALID.replace("[\"RF_CHEST\"] }, \"outcome\"", "[\"RF_TYPO\"] }, \"outcome\"");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RF_TYPO");
    }

    @Test
    void scoringWeightForUnknownSpecialty_isRejected() {
        String json = VALID.replace("\"GI\": 5", "\"XX\": 5");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không có trong 'specialties'");
    }

    @Test
    void scoringWeightOutOfRange_isRejected() {
        String json = VALID.replace("\"GI\": 5", "\"GI\": 9");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trọng số");
    }

    @Test
    void escalationOutcomePass_isRejected() {
        String json = VALID.replace("\"outcome\": \"EMERGENCY\"", "\"outcome\": \"PASS\"");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMERGENCY hoặc REFER");
    }

    @Test
    void showIfReferencingLaterQuestion_isRejected() {
        // Q_RF chỉ hiện khi Q_AREA = BELLY, nhưng Q_AREA đứng SAU Q_RF
        String json = VALID.replace("\"code\": \"Q_RF\", \"text\": \"Dấu hiệu?\", \"type\": \"MULTI\",",
                "\"code\": \"Q_RF\", \"text\": \"Dấu hiệu?\", \"type\": \"MULTI\", \"showIf\": { \"Q_AREA\": [\"BELLY\"] },");

        assertThatThrownBy(() -> TriageRuleLoader.parse(json))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SAU câu này");
    }
}
