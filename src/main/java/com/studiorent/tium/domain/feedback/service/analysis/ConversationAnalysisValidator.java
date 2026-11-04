package com.studiorent.tium.domain.feedback.service.analysis;

import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisInput;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationAnalysisResult;
import com.studiorent.tium.domain.feedback.dto.analysis.UtterancePurpose;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 구조 및 원문과의 일치를 검증한다.
 */
@Component
public class ConversationAnalysisValidator {

    private final Validator validator; //DTO 에 붙은 NotBlank, NotNull,  @Vaild등의 조건을 검사해줌

    public ConversationAnalysisValidator(Validator validator) {
        this.validator = validator;
    }

    public void validateInput(ConversationAnalysisInput input) { // 분석할 대화 검사
        if (input == null || !validator.validate(input).isEmpty()) { // 내용이없다면  잘못된 요청
            throw new BusinessException(ErrorStatus.BAD_REQUEST);
        }

        Set<String> messageIds = new HashSet<>();
        boolean targetPresent = false;
        for (var message : input.messages()) {
            if (!messageIds.add(message.messageId())) { // 해쉬set은 이미 있는값을 넣으면 false를 반환, 대화순서가 중복으로 들어가는지확인하는가 과정
                throw new BusinessException(ErrorStatus.BAD_REQUEST);
            }
            targetPresent |= message.speaker() == input.targetSpeaker(); // targetPresent |= 은 한번이라도 타켓하는 대상이 등장했다면 true유지,
        }
        if (!targetPresent) {
            throw new BusinessException(ErrorStatus.BAD_REQUEST);
        }
    }





    public void validateResult(ConversationAnalysisInput input, ConversationAnalysisResult result) { //AI 분석 결과 검사
        validateInput(input);
        if (result == null || !validator.validate(result).isEmpty()) {
            throw invalidResponse();
        }

        Set<String> messageIds = new HashSet<>();
        input.messages().forEach(message -> messageIds.add(message.messageId())); // 인풋데이터 : 대화 메세지 ID
        Set<String> contextIds = new HashSet<>(result.contextEvidenceMessageIds()); // 아웃풋 데이터 : 대화 근거 원본 아이디 set으로 저장


        if (!messageIds.containsAll(contextIds) // AI가 맥락의 근거로 든 ID들이 전부 원본에 있는가
                || contextIds.size() != result.contextEvidenceMessageIds().size()
                || result.utterances().size() != input.messages().size()) {
            throw invalidResponse();
        }

        for (int index = 0; index < input.messages().size(); index++) { // 발화면 테스트
            var message = input.messages().get(index);
            var analysis = result.utterances().get(index);
            // 개수와 순서를 대조하면 발화 누락, 중복, 존재하지 않는 ID도 거를 수 있다.
            if (!message.messageId().equals(analysis.messageId())) { // 서로 같은 대화인지 확인
                throw invalidResponse();
            }

            Set<UtterancePurpose> purposes = new HashSet<>();
            for (var candidate : analysis.purposeCandidates()) {
                if (!purposes.add(candidate.purpose()) || !message.text().contains(candidate.evidenceQuote())) {
                    throw invalidResponse();   //실제로 인용된건지 or 발화별 목적은 중복이 될수있지만 결과로는 중복이되어선안된다.
                }
            }
            if (purposes.contains(UtterancePurpose.UNKNOWN)  // 발화별 목적이 알수없을 경우, 목적은 하나만 쓴다
                    && (purposes.size() != 1 || analysis.uncertainties().isEmpty())) {
                throw invalidResponse();
            }

            validateQuotes(message.text(), analysis.explicitEmotionQuotes());
            validateQuotes(message.text(), analysis.explicitRequestQuotes());
        }
    }

    private void validateQuotes(String text, List<String> quotes) {
        if (quotes.stream().anyMatch(quote -> !text.contains(quote))) { // 인용하는 부분에서 인용되지않은부분이 나올 경우
            throw invalidResponse();
        }
    }

    private BusinessException invalidResponse() {
        return new BusinessException(ErrorStatus.FEEDBACK_ANALYSIS_INVALID_RESPONSE);
    }
}
