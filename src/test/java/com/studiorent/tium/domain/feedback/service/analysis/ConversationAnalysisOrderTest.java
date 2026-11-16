package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Speaker;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.UtteranceAnalysis;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ConversationAnalysisOrderTest {
    private final List<Utterance> source = List.of(new Utterance("a", Speaker.ME, "첫 말"), new Utterance("b", Speaker.OTHER, "둘째 말"));
    private UtteranceAnalysis item(String id) {
        return new UtteranceAnalysis(id, List.of(), List.of(), List.of(), List.of("내용 보존 확인"));
    }
    private ConversationAnalysisResult result(UtteranceAnalysis... items) {
        return new ConversationAnalysisResult("원래 요약", List.of("a"), List.of(items), List.of("원래 불확실성"));
    }
    @Test
    void 모든_ID가_있을_때_내용을_바꾸지_않고_순서만_복원한다() {
        var a = item("a"); var b = item("b"); var original = result(b, a);
        var restored = ConversationAnalysisOrder.restore(source, original);
        assertThat(restored.utterances()).containsExactly(a, b);
        assertThat(restored.utterances().getFirst()).isSameAs(a);
        assertThat(restored.contextSummary()).isEqualTo(original.contextSummary());
        assertThat(restored.contextEvidenceMessageIds()).isEqualTo(original.contextEvidenceMessageIds());
        assertThat(restored.uncertainties()).isEqualTo(original.uncertainties());
        assertThat(original.utterances()).containsExactly(b, a);
    }
    @Test
    void 누락_중복_외부_ID는_보충하거나_삭제하지_않는다() {
        for (var invalid : List.of(result(item("a")), result(item("a"), item("a")), result(item("a"), item("c")))) {
            assertThat(ConversationAnalysisOrder.restore(source, invalid)).isSameAs(invalid);
        }
        assertThat(ConversationAnalysisOrder.restore(source, null)).isNull();
    }
}
