package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.rules.TriageRules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Test toàn bộ bộ máy: kịch bản cụ thể + duyệt mọi đường đi để kiểm tra các bất biến an toàn.
public class TriageFlowEngineTest {

    private static TriageRules rules;
    private static EscalationEvaluator evaluator;
    private static TriageFlowEngine engine;

    @BeforeAll
    static void init() {
        TriageRuleBook book = new TriageRuleBook();
        rules = book.rules();
        evaluator = new EscalationEvaluator(book);
        engine = new TriageFlowEngine(book, new TriageAnswerValidator(book), evaluator,
                new TriageScorer(book), new TriageResultGate(book));
    }

    // ===== Kịch bản cụ thể =====

    // status: kết quả mong đợi. detail:
    //   PASS               -> mã chuyên khoa được gợi ý
    //   REFER / EMERGENCY  -> "ESC_..." nếu do luật leo thang, ngược lại là lý do của Gate (LOW_SCORE, LOW_MARGIN...)
    record Scenario(String name, TriageFlowEngine.Status status, String detail, Map<String, String> answers) {
        @Override
        public String toString() {
            return name;
        }
    }

    // Người lớn, nhẹ, không bệnh nền; mỗi kịch bản chỉ ghi các đáp án khác mặc định
    private static Scenario row(String name, TriageFlowEngine.Status status, String detail, String... overrides) {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("AGE", "ADULT");
        a.put("Q_RED_FLAGS", "RF_NONE");
        a.put("Q_CARE_GROUP", "CARE_NONE");
        a.put("Q_EXTRA", "EXTRA_NONE");
        a.put("Q_IMPACT", "MILD");
        a.put("Q_PATTERN", "OCCASIONAL");
        a.put("Q_QUALITY", "DULL");
        a.put("Q_ONSET", "DAYS_1_3");
        for (int i = 0; i < overrides.length; i += 2) {
            a.put(overrides[i], overrides[i + 1]);
        }
        return new Scenario(name, status, detail, a);
    }

    static Stream<Scenario> scenarios() {
        return Stream.of(
                row("đau bụng trên → Tiêu hóa", TriageFlowEngine.Status.PASS, "GI", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_UPPER"),
                row("đau bụng trên quặn thắt → Tiêu hóa", TriageFlowEngine.Status.PASS, "GI", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_UPPER", "Q_QUALITY", "CRAMPING"),
                row("đau lưng cổ vai gáy → Xương khớp", TriageFlowEngine.Status.PASS, "MSK", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "BACK_NECK"),
                row("đau khớp → Xương khớp", TriageFlowEngine.Status.PASS, "MSK", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "JOINT"),
                row("đau cơ tay chân → Xương khớp", TriageFlowEngine.Status.PASS, "MSK", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "MUSCLE_LIMB"),
                row("nhức đầu một bên → Thần kinh", TriageFlowEngine.Status.PASS, "NEURO", "Q_COMPLAINT", "HEADACHE", "Q_HEAD_AREA", "ONE_SIDE"),
                row("nhức đầu cả đầu → Thần kinh", TriageFlowEngine.Status.PASS, "NEURO", "Q_COMPLAINT", "HEADACHE", "Q_HEAD_AREA", "BOTH_SIDES"),
                row("ho khan 1–3 ngày → Nội khoa", TriageFlowEngine.Status.PASS, "INTERNAL", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY"),
                row("ho có đờm 1–3 ngày → Nội khoa", TriageFlowEngine.Status.PASS, "INTERNAL", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "PHLEGM"),
                row("ho khan hơn 3 tuần → Hô hấp", TriageFlowEngine.Status.PASS, "RESP", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY", "Q_ONSET", "OVER_3_WEEKS"),
                row("ho khò khè → Hô hấp", TriageFlowEngine.Status.PASS, "RESP", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "WHEEZE"),
                row("ho khò khè kèm hụt hơi khi gắng sức vẫn → Hô hấp", TriageFlowEngine.Status.PASS, "RESP", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "WHEEZE", "Q_EXTRA", "BREATHLESS_EXERTION"),
                row("đau họng → Tai mũi họng", TriageFlowEngine.Status.PASS, "ENT", "Q_COMPLAINT", "ENT", "Q_ENT_TYPE", "SORE_THROAT"),
                row("nghẹt mũi → Tai mũi họng", TriageFlowEngine.Status.PASS, "ENT", "Q_COMPLAINT", "ENT", "Q_ENT_TYPE", "NOSE"),
                row("đau tai → Tai mũi họng", TriageFlowEngine.Status.PASS, "ENT", "Q_COMPLAINT", "ENT", "Q_ENT_TYPE", "EAR"),
                row("mắt đỏ ngứa → Mắt", TriageFlowEngine.Status.PASS, "EYE", "Q_COMPLAINT", "EYE", "Q_EYE_TYPE", "RED_ITCHY"),
                row("nhìn mờ dần → Mắt", TriageFlowEngine.Status.PASS, "EYE", "Q_COMPLAINT", "EYE", "Q_EYE_TYPE", "BLURRING"),
                row("đau răng → Răng hàm mặt", TriageFlowEngine.Status.PASS, "DENTAL", "Q_COMPLAINT", "DENTAL", "Q_DENTAL_TYPE", "TOOTHACHE"),
                row("loét miệng mới vài ngày → Răng hàm mặt", TriageFlowEngine.Status.PASS, "DENTAL", "Q_COMPLAINT", "DENTAL", "Q_DENTAL_TYPE", "MOUTH_ULCER"),
                row("nổi mẩn ngứa → Da liễu", TriageFlowEngine.Status.PASS, "DERMA", "Q_COMPLAINT", "SKIN", "Q_SKIN_TYPE", "RASH_ITCH"),
                row("mụn → Da liễu", TriageFlowEngine.Status.PASS, "DERMA", "Q_COMPLAINT", "SKIN", "Q_SKIN_TYPE", "ACNE"),
                row("buồn nôn, nôn → Tiêu hóa", TriageFlowEngine.Status.PASS, "GI", "Q_COMPLAINT", "DIGESTIVE", "Q_DIGESTIVE_TYPE", "NAUSEA_VOMIT"),
                row("tiêu chảy → Tiêu hóa", TriageFlowEngine.Status.PASS, "GI", "Q_COMPLAINT", "DIGESTIVE", "Q_DIGESTIVE_TYPE", "DIARRHEA"),
                row("bong gân → Xương khớp", TriageFlowEngine.Status.PASS, "MSK", "Q_COMPLAINT", "INJURY", "Q_INJURY_TYPE", "SPRAIN"),
                row("sốt 1–3 ngày → Nội khoa", TriageFlowEngine.Status.PASS, "INTERNAL", "Q_COMPLAINT", "FEVER"),
                row("mệt mỏi → Nội khoa", TriageFlowEngine.Status.PASS, "INTERNAL", "Q_COMPLAINT", "FATIGUE"),
                row("hồi hộp thỉnh thoảng → Tim mạch", TriageFlowEngine.Status.PASS, "CARDIO", "Q_COMPLAINT", "PALPITATION"),
                row("trẻ 1–14 tuổi bị ho → Nhi khoa", TriageFlowEngine.Status.PASS, "PEDIATRIC", "AGE", "CHILD", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY"),
                row("trẻ 1–14 tuổi đau răng → Răng hàm mặt", TriageFlowEngine.Status.PASS, "DENTAL", "AGE", "CHILD", "Q_COMPLAINT", "DENTAL", "Q_DENTAL_TYPE", "TOOTHACHE"),
                row("người từ 60 tuổi ho 1–3 ngày → Nội khoa", TriageFlowEngine.Status.PASS, "INTERNAL", "AGE", "SENIOR", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY"),
                row("đau bụng dưới vị trí khác → chuyển (điểm thấp)", TriageFlowEngine.Status.REFER, "LOW_SCORE", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_LOWER_OTHER"),
                row("đau bụng dưới vị trí khác quặn thắt → chuyển (cách biệt nhỏ)", TriageFlowEngine.Status.REFER, "LOW_MARGIN", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_LOWER_OTHER", "Q_QUALITY", "CRAMPING"),
                row("nhức đầu trán → chuyển (điểm thấp)", TriageFlowEngine.Status.REFER, "LOW_SCORE", "Q_COMPLAINT", "HEADACHE", "Q_HEAD_AREA", "FOREHEAD_EYES"),
                row("nhức đầu trán tức nặng → chuyển (cách biệt nhỏ)", TriageFlowEngine.Status.REFER, "LOW_MARGIN", "Q_COMPLAINT", "HEADACHE", "Q_HEAD_AREA", "FOREHEAD_EYES", "Q_QUALITY", "PRESSURE"),
                row("nhức đầu sau gáy → chuyển", TriageFlowEngine.Status.REFER, "LOW_SCORE", "Q_COMPLAINT", "HEADACHE", "Q_HEAD_AREA", "BACK_OF_HEAD"),
                row("chóng mặt quay cuồng → chuyển", TriageFlowEngine.Status.REFER, "LOW_SCORE", "Q_COMPLAINT", "DIZZY", "Q_DIZZY_TYPE", "SPINNING"),
                row("chóng mặt choáng sắp ngất → chuyển", TriageFlowEngine.Status.REFER, "LOW_SCORE", "Q_COMPLAINT", "DIZZY", "Q_DIZZY_TYPE", "FAINTNESS"),
                row("hụt hơi, khó thở nhẹ → chuyển", TriageFlowEngine.Status.REFER, "LOW_SCORE", "Q_COMPLAINT", "BREATH"),
                row("vùng da sưng đỏ nóng đau → chuyển", TriageFlowEngine.Status.REFER, "LOW_SCORE", "Q_COMPLAINT", "SKIN", "Q_SKIN_TYPE", "SKIN_INFECTION"),
                row("đau ngực → chuyển", TriageFlowEngine.Status.REFER, "ESC_CHEST_PAIN", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "CHEST"),
                row("đau bụng dưới bên phải → chuyển", TriageFlowEngine.Status.REFER, "ESC_ABDOMEN_RIGHT_OR_ALL", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_LOWER_RIGHT"),
                row("đau khắp bụng → chuyển", TriageFlowEngine.Status.REFER, "ESC_ABDOMEN_RIGHT_OR_ALL", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_ALL"),
                row("đau bụng trên mới vài giờ và khá nặng → chuyển", TriageFlowEngine.Status.REFER, "ESC_ACUTE_ABDOMEN", "Q_COMPLAINT", "PAIN", "Q_PAIN_AREA", "ABD_UPPER", "Q_ONSET", "HOURS", "Q_IMPACT", "MODERATE"),
                row("chóng mặt loạng choạng → chuyển", TriageFlowEngine.Status.REFER, "ESC_DIZZY_UNSTEADY", "Q_COMPLAINT", "DIZZY", "Q_DIZZY_TYPE", "UNSTEADY"),
                row("sốt 4–7 ngày → chuyển", TriageFlowEngine.Status.REFER, "ESC_FEVER_PROLONGED", "Q_COMPLAINT", "FEVER", "Q_ONSET", "DAYS_4_7"),
                row("hồi hộp liên tục → chuyển", TriageFlowEngine.Status.REFER, "ESC_PALPITATION_CONTINUOUS", "Q_COMPLAINT", "PALPITATION", "Q_PATTERN", "CONTINUOUS"),
                row("loét miệng hơn 3 tuần → chuyển", TriageFlowEngine.Status.REFER, "ESC_MOUTH_ULCER_LONG", "Q_COMPLAINT", "DENTAL", "Q_DENTAL_TYPE", "MOUTH_ULCER", "Q_ONSET", "OVER_3_WEEKS"),
                row("vết cắt, trầy → chuyển", TriageFlowEngine.Status.REFER, "ESC_CUT", "Q_COMPLAINT", "INJURY", "Q_INJURY_TYPE", "CUT"),
                row("khiếu nại 'khác' → chuyển", TriageFlowEngine.Status.REFER, "ESC_OTHER_COMPLAINT", "Q_COMPLAINT", "OTHER"),
                row("trẻ dưới 1 tuổi → chuyển ngay", TriageFlowEngine.Status.REFER, "ESC_INFANT", "AGE", "INFANT"),
                row("ho ngày càng nặng hơn → chuyển", TriageFlowEngine.Status.REFER, "ESC_WORSENING", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY", "Q_PATTERN", "WORSENING"),
                row("mức ảnh hưởng nặng → chuyển", TriageFlowEngine.Status.REFER, "ESC_SEVERE_IMPACT", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY", "Q_IMPACT", "SEVERE"),
                row("đang mang thai → chuyển", TriageFlowEngine.Status.REFER, "ESC_PREGNANT", "Q_COMPLAINT", "SKIN", "Q_SKIN_TYPE", "RASH_ITCH", "Q_CARE_GROUP", "PREGNANT"),
                row("đái tháo đường và ho khá nặng → chuyển", TriageFlowEngine.Status.REFER, "ESC_CARE_GROUP_MODERATE", "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY", "Q_CARE_GROUP", "DIABETES", "Q_IMPACT", "MODERATE"),
                row("đái tháo đường kèm sốt → chuyển", TriageFlowEngine.Status.REFER, "ESC_CARE_GROUP_FEVER", "Q_COMPLAINT", "FEVER", "Q_CARE_GROUP", "DIABETES"),
                row("tê hoặc yếu tay chân → chuyển", TriageFlowEngine.Status.REFER, "ESC_NUMBNESS", "Q_COMPLAINT", "FATIGUE", "Q_EXTRA", "NUMBNESS"),
                row("sụt cân không rõ lý do → chuyển", TriageFlowEngine.Status.REFER, "ESC_WEIGHT_LOSS", "Q_COMPLAINT", "FATIGUE", "Q_EXTRA", "WEIGHT_LOSS"),
                row("người từ 60 tuổi bị sốt → chuyển", TriageFlowEngine.Status.REFER, "ESC_FEVER_SENIOR", "AGE", "SENIOR", "Q_COMPLAINT", "FEVER"),
                row("nhức đầu kèm sốt → chuyển", TriageFlowEngine.Status.REFER, "ESC_HEADACHE_FEVER", "Q_COMPLAINT", "HEADACHE", "Q_HEAD_AREA", "ONE_SIDE", "Q_EXTRA", "FEVER"),
                row("nhức đầu mới vài giờ và khá nặng → chuyển", TriageFlowEngine.Status.REFER, "ESC_HEADACHE_ACUTE", "Q_COMPLAINT", "HEADACHE", "Q_HEAD_AREA", "ONE_SIDE", "Q_ONSET", "HOURS", "Q_IMPACT", "MODERATE"),
                row("người từ 60 tuổi chóng mặt khá nặng → chuyển", TriageFlowEngine.Status.REFER, "ESC_DIZZY_SENIOR", "AGE", "SENIOR", "Q_COMPLAINT", "DIZZY", "Q_DIZZY_TYPE", "SPINNING", "Q_IMPACT", "MODERATE")
        );
    }

    // Trả lời từng câu được hỏi, theo đúng thứ tự engine yêu cầu, cho tới khi có kết quả cuối
    private static TriageFlowEngine.TriageStep run(Map<String, String> given) {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            TriageFlowEngine.TriageStep step = engine.next(answers);
            if (step.status() != TriageFlowEngine.Status.QUESTION) {
                return step;
            }
            String code = step.question().code();
            String value = given.get(code);
            if (value == null) {
                throw new AssertionError("Kịch bản chưa có đáp án cho câu: " + code);
            }
            answers.put(code, List.of(value.split(",")));
        }
        throw new AssertionError("Quá 30 câu hỏi — luồng bị lặp?");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenario_givesExpectedResult(Scenario s) {
        TriageFlowEngine.TriageStep step = run(s.answers());

        assertThat(step.status()).isEqualTo(s.status());
        if (s.status() == TriageFlowEngine.Status.PASS) {
            assertThat(step.gate().specialtyCode()).isEqualTo(s.detail());
        } else if (s.detail().startsWith("ESC_")) {
            assertThat(step.escalations()).extracting(TriageRules.EscalationRule::id).contains(s.detail());
        } else {
            assertThat(step.gate().reason()).isEqualTo(s.detail());
        }
    }

    // ===== Dấu hiệu nguy hiểm =====

    @Test
    void firstQuestion_isAge() {
        TriageFlowEngine.TriageStep step = engine.next(new LinkedHashMap<>());

        assertThat(step.status()).isEqualTo(TriageFlowEngine.Status.QUESTION);
        assertThat(step.question().code()).isEqualTo("AGE");
    }

    @Test
    void redFlagQuestion_cannotBeSkipped() {
        // Client trả lời thẳng khiếu nại mà bỏ qua câu dấu hiệu nguy hiểm: vẫn bị hỏi lại câu đó
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("AGE", List.of("ADULT"));
        answers.put("Q_COMPLAINT", List.of("COUGH"));

        TriageFlowEngine.TriageStep step = engine.next(answers);

        assertThat(step.status()).isEqualTo(TriageFlowEngine.Status.QUESTION);
        assertThat(step.question().code()).isEqualTo("Q_RED_FLAGS");
    }

    @Test
    void everyRedFlag_isEmergency_evenWhenEverythingElseLooksMild() {
        for (TriageRules.Option o : rules.questions().get(1).options()) {
            if (o.exclusive() || o.code().equals("RF_SELF_HARM")) continue;

            Map<String, String> given = row("x", TriageFlowEngine.Status.PASS, "INTERNAL",
                    "Q_COMPLAINT", "COUGH", "Q_COUGH_TYPE", "DRY", "Q_RED_FLAGS", o.code()).answers();

            assertThat(run(given).status()).as("dấu hiệu %s", o.code()).isEqualTo(TriageFlowEngine.Status.EMERGENCY);
        }
    }

    @Test
    void selfHarmTogetherWithAnotherFlag_returnsBothRules() {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("AGE", List.of("ADULT"));
        answers.put("Q_RED_FLAGS", List.of("RF_CHEST", "RF_SELF_HARM"));

        TriageFlowEngine.TriageStep step = engine.next(answers);

        assertThat(step.status()).isEqualTo(TriageFlowEngine.Status.EMERGENCY);
        assertThat(step.escalations()).extracting(TriageRules.EscalationRule::id)
                .containsExactlyInAnyOrder("ESC_RED_FLAGS", "ESC_SELF_HARM");
    }

    @Test
    void invalidAnswers_areRejected() {
        Map<String, List<String>> answers = new LinkedHashMap<>();
        answers.put("AGE", List.of("KHONG_CO"));

        assertThatThrownBy(() -> engine.next(answers)).isInstanceOf(IllegalStateException.class);
    }

    // ===== Duyệt MỌI đường đi (mỗi câu thử từng đáp án đơn lẻ) =====

    private int leaves;
    private int passLeaves;
    private int maxAnswered;
    private final Set<String> passSpecialties = new TreeSet<>();

    @Test
    void everyPath_respectsSafetyInvariants() {
        walk(new LinkedHashMap<>());

        assertThat(passLeaves).isGreaterThan(0);
        // Mọi chuyên khoa trong bộ luật đều có đường dẫn ra PASS (không có chuyên khoa "chết")
        assertThat(passSpecialties).isEqualTo(new TreeSet<>(rules.specialties().keySet()));
        // Không đường nào hỏi quá 10 câu
        assertThat(maxAnswered).isLessThanOrEqualTo(10);
    }

    private void walk(Map<String, List<String>> answers) {
        TriageFlowEngine.TriageStep step = engine.next(answers);

        if (step.status() == TriageFlowEngine.Status.QUESTION) {
            for (TriageRules.Option o : step.question().options()) {
                Map<String, List<String>> copy = new LinkedHashMap<>(answers);
                copy.put(step.question().code(), List.of(o.code()));
                walk(copy);
            }
            return;
        }

        leaves++;
        maxAnswered = Math.max(maxAnswered, answers.size());
        if (step.status() != TriageFlowEngine.Status.PASS) {
            return;
        }

        passLeaves++;
        passSpecialties.add(step.gate().specialtyCode());

        // Bất biến 1: PASS không bao giờ đi cùng một luật leo thang đang khớp
        assertThat(evaluator.evaluate(answers)).as("PASS nhưng vẫn có luật leo thang: %s", answers).isEmpty();

        // Bất biến 2: thêm BẤT KỲ dấu hiệu nguy hiểm nào vào một ca đang PASS đều ra EMERGENCY
        // (lấy mẫu 1/50 ca PASS để test chạy nhanh)
        if (passLeaves % 50 == 0) {
            for (TriageRules.Option flag : rules.questions().get(1).options()) {
                if (flag.exclusive()) continue;
                Map<String, List<String>> withFlag = new LinkedHashMap<>(answers);
                withFlag.put("Q_RED_FLAGS", List.of(flag.code()));
                assertThat(engine.next(withFlag).status())
                        .as("thêm dấu hiệu %s vào ca PASS", flag.code())
                        .isEqualTo(TriageFlowEngine.Status.EMERGENCY);
            }
        }
    }

}
