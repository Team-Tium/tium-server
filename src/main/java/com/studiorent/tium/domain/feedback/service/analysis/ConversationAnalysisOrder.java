package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.Utterance;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.UtteranceAnalysis;

import java.util.HashMap;
import java.util.List;

/** 모든 원문 ID가 정확히 한 번 존재할 때에만 원문 순서를 복원한다. 분석 내용은 수정하지 않는다. */
public final class ConversationAnalysisOrder {
    private ConversationAnalysisOrder() {
    }

    public static ConversationAnalysisResult restore(List<Utterance> messages, ConversationAnalysisResult result) {
        if (result == null || result.utterances() == null || result.utterances().size() != messages.size()) {
            return result; // 누락·초과는 기존 검증기에서 거절한다.
        }
        var byId = new HashMap<String, UtteranceAnalysis>();
        for (var item : result.utterances()) {
            if (item == null || item.messageId() == null || byId.putIfAbsent(item.messageId(), item) != null) {
                return result;
            }
        }
        if (messages.stream().anyMatch(message -> !byId.containsKey(message.messageId()))) {
            return result;
        }
        return new ConversationAnalysisResult(result.contextSummary(), result.contextEvidenceMessageIds(),
                messages.stream().map(message -> byId.get(message.messageId())).toList(), result.uncertainties());
    }
}
