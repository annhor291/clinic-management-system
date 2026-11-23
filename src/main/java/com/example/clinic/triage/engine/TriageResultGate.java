package com.example.clinic.triage.engine;

import com.example.clinic.triage.TriageRuleBook;
import com.example.clinic.triage.rules.TriageOutcome;
import com.example.clinic.triage.rules.TriageRules;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

// "Người duyệt": chỉ nhìn PHIẾU ĐIỂM đối chiếu với bảng ngưỡng, không biết người dùng đã trả lời gì.
// Chỉ có hai kết quả: PASS hoặc REFER. Không bao giờ nâng một ca đã bị leo thang lên PASS
// (việc đó đã được chặn từ trước, vì Gate chỉ được gọi khi không có luật leo thang nào khớp).
@Component
public class TriageResultGate {

    public record Ranked(String specialtyCode, int score) {}

    // reason: OK | NO_SCORE | LOW_SCORE | LOW_MARGIN | NO_STRONG_SIGNAL
    public record GateDecision(TriageOutcome outcome, String specialtyCode, List<Ranked> ranking, String reason) {}

    private final TriageRules.Gate gate;

    public TriageResultGate(TriageRuleBook book) {
        this.gate = book.rules().gate();
    }

    public GateDecision decide(TriageScorer.ScoreCard card) {
        // Điểm cao trước; bằng điểm thì xếp theo mã để kết quả luôn giống nhau giữa các lần chạy
        List<Ranked> ranking = card.scores().entrySet().stream()
                .map(e -> new Ranked(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingInt(Ranked::score).reversed()
                        .thenComparing(Ranked::specialtyCode))
                .toList();

        if (ranking.isEmpty()) {
            return refer(ranking, "NO_SCORE");
        }

        Ranked top = ranking.get(0);
        int second = ranking.size() > 1 ? ranking.get(1).score() : 0;

        if (top.score() < gate.minTopScore()) {
            return refer(ranking, "LOW_SCORE");
        }
        if (top.score() - second < gate.minMargin()) {
            return refer(ranking, "LOW_MARGIN");
        }
        if (card.strongest().getOrDefault(top.specialtyCode(), 0) < gate.strongWeight()) {
            return refer(ranking, "NO_STRONG_SIGNAL");
        }
        return new GateDecision(TriageOutcome.PASS, top.specialtyCode(), ranking, "OK");
    }

    private static GateDecision refer(List<Ranked> ranking, String reason) {
        return new GateDecision(TriageOutcome.REFER, null, ranking, reason);
    }
}
