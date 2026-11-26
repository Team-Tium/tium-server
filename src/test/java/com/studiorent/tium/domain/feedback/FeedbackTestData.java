package com.studiorent.tium.domain.feedback;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput.*;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult.*;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation.*;
import com.studiorent.tium.domain.feedback.dto.analysis.UtterancePurpose;
import com.studiorent.tium.domain.feedback.entity.enums.OverallQuality;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import java.util.List;

public final class FeedbackTestData {
    private FeedbackTestData() { }
    public static ConversationAnalysisInput input(boolean call) {
        String prefix = call ? "stt:" : "chat:";
        return new ConversationAnalysisInput(RelationShip.UNCONFIRMED, Speaker.ME, List.of(
                new Utterance(prefix + "1", Speaker.OTHER, "내일 오전에 보낼 수 있어."),
                new Utterance(prefix + "2", Speaker.ME, "그럼 내일 오전에 보내줘.")),
                call ? new Scope(200, "통화의 최근 최대 200개 발화") : null);
    }
    public static ConversationAnalysisResult analysis(ConversationAnalysisInput input) {
        var first = input.messages().getFirst(); var second = input.messages().getLast();
        var ids = input.messages().stream().map(Utterance::messageId).toList();
        return new ConversationAnalysisResult("파일 전달 시점을 이야기한다.", ids, List.of(
                new UtteranceAnalysis(first.messageId(), List.of(new PurposeCandidate(
                        UtterancePurpose.INFORMATION_SHARING, first.text(), "가능한 시점을 설명한다.")), List.of(), List.of(), List.of()),
                new UtteranceAnalysis(second.messageId(), List.of(new PurposeCandidate(
                        UtterancePurpose.OTHER, second.text(), "현재 상대에게 파일 전달을 부탁한다.")), List.of(),
                        List.of(new RequestAnalysis(second.text(), RequestParty.ME, RequestParty.OTHER,
                                RequestAddressing.DIRECT, "파일 전달", ids, "현재 상대에게 내일 전달을 요청한다.")), List.of())), List.of());
    }
    public static ConversationFeedbackEvaluation evaluation(ConversationAnalysisInput input) {
        var first = input.messages().getFirst(); var second = input.messages().getLast();
        var evidence = new Evidence(second.messageId(), second.text());
        return new ConversationFeedbackEvaluation(Speaker.ME, OverallQuality.GOOD, "상대가 가능한 일정에 맞춘 응답이다.",
                false, "", "파일 전달 일정 조율", List.of(new ResponseAssessment(List.of(second.messageId()),
                List.of(first.messageId()), "일정 조율", Finding.HELPFUL, Impact.NONE, false, List.of(evidence),
                "상대가 가능한 시간을 반영해 요청했어요.")),
                "상대가 가능한 시간을 받아들이고 내일 오전에 보내 달라고 요청했어요.",
                List.of(new Strength("가능한 시간에 맞춰 일정을 정했어요.", List.of(evidence))), List.of(), List.of());
    }
}
