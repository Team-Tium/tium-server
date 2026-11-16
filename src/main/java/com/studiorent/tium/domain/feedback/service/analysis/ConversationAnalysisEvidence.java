package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.PurposeCandidate;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.UtteranceAnalysis;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 목적의 평가 단위는 해당 발화 전체다. 원문 인용을 모델이 다시 작성하게 하지 않는다. */
@Slf4j
final class ConversationAnalysisEvidence {
    private ConversationAnalysisEvidence() { }

    static ConversationAnalysisResult bindOriginalUtterances(List<Utterance> source, ConversationAnalysisResult result) {
        if (result == null || result.utterances() == null) {
            return result;
        }
        Map<String, String> originals = source.stream().collect(Collectors.toMap(Utterance::messageId, Utterance::text));
        var utterances = result.utterances().stream().map(item -> {
            if (item == null || !originals.containsKey(item.messageId()) || item.purposeCandidates() == null) {
                return item;
            }
            var candidates = item.purposeCandidates().stream().map(candidate -> candidate == null ? null
                    : new PurposeCandidate(candidate.purpose(), originals.get(item.messageId()), candidate.reason())).toList();
            // 감정 인용은 해당 화자의 표현만 허용한다. 다른 화자의 말이나 재작성된 문장을 원문으로 바꾸어 감정을 만들어내지 않는다.
            var emotions = item.explicitEmotionQuotes() == null ? null : item.explicitEmotionQuotes().stream()
                    .filter(quote -> quote != null && originals.get(item.messageId()).contains(quote)).toList();
            if (emotions != null && emotions.size() != item.explicitEmotionQuotes().size()) {
                log.warn("Conversation analysis omitted unsupported emotion quotes: count={}",
                        item.explicitEmotionQuotes().size() - emotions.size());
            }
            return new UtteranceAnalysis(item.messageId(), candidates, emotions,
                    item.requests(), item.uncertainties());
        }).toList();
        return new ConversationAnalysisResult(result.contextSummary(), result.contextEvidenceMessageIds(),
                utterances, result.uncertainties());
    }
}
