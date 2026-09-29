package com.studiorent.tium.domain.feedback.service.command;

import com.studiorent.tium.domain.chat.entity.ChatMessage;
import com.studiorent.tium.domain.chat.entity.enums.MessageType;
import com.studiorent.tium.domain.chat.repository.ChatMessageRepository;
import com.studiorent.tium.domain.chat.service.ChatRoomValidator;
import com.studiorent.tium.domain.call.service.CallAccessValidator;
import com.studiorent.tium.domain.call.service.CallSttService;
import com.studiorent.tium.domain.feedback.converter.FeedbackConverter;
import com.studiorent.tium.domain.feedback.dto.ConversationTurn;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackRequestDTOv1;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import com.studiorent.tium.domain.feedback.entity.Feedback;
import com.studiorent.tium.domain.feedback.entity.enums.RelationShip;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import jakarta.annotation.Nullable;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
public class FeedbackCommandServiceImpl implements FeedbackCommandService {

    private static final int FEEDBACK_MESSAGE_LIMIT = 100;
    private static final long CALL_FEEDBACK_PROCESSING_TIMEOUT_MINUTES = 15;
    private static final int FEEDBACK_INVALID_RESPONSE_RETRY_COUNT = 2;

    private final FeedbackRepository feedbackRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatClient chatClient;
    private final ChatRoomValidator chatRoomValidator;
    private final CallAccessValidator callAccessValidator;
    private final CallSttService callSttService;
    private final TransactionTemplate transactionTemplate;

    public FeedbackCommandServiceImpl(
            FeedbackRepository feedbackRepository,
            ChatMessageRepository chatMessageRepository,
            ChatClient.Builder chatClientBuilder,
            Advisor[] advisors,
            ChatRoomValidator chatRoomValidator,
            CallAccessValidator callAccessValidator,
            CallSttService callSttService,
            PlatformTransactionManager transactionManager
    ) {
        this.feedbackRepository = feedbackRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatRoomValidator = chatRoomValidator;
        this.callAccessValidator = callAccessValidator;
        this.callSttService = callSttService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.chatClient = chatClientBuilder
                .defaultOptions(ChatOptions.builder().temperature(0.0))
                .defaultAdvisors(advisors)
                .build();
    }


    @Override
    @Transactional
    public FeedbackResponseDTOv1 createFeedback(Long memberId, Long roomId) {
        FeedbackRequestDTOv1 feedbackRequest = FeedbackRequestDTOv1.defaultRequest();

        // 피드백은 로그인한 사용자가 실제로 참여 중인 채팅방에 대해서만 생성할 수 있다.
        chatRoomValidator.getJoinedMember(roomId, memberId);

        // 프론트에서 대화 내용을 받지 않고, roomId로 저장된 채팅 메시지를 직접 가져온다.
        List<ConversationTurn> conversation = getConversationFromChatMessages(memberId, roomId);

        FeedbackResponseDTOv1 result = generateFeedback(feedbackRequest, conversation);

        Feedback feedback = FeedbackConverter.toFeedback(memberId, roomId, result);
        feedbackRepository.save(feedback);

        return result;
    }

    @Override
    public FeedbackResponseDTOv1 createCallFeedback(Long memberId, Long callId) {
        FeedbackRequestDTOv1 feedbackRequest = FeedbackRequestDTOv1.defaultRequest();
        callAccessValidator.validateParticipant(callId, memberId);

        Optional<FeedbackResponseDTOv1> existingFeedback = findExistingCallFeedback(memberId, callId);
        if (existingFeedback.isPresent()) {
            return existingFeedback.get();
        }

        Feedback myClaim = claimCallFeedback(memberId, callId);

        try {
            List<ConversationTurn> conversation = callSttService.findConversationForFeedback(memberId, callId);
            FeedbackResponseDTOv1 myFeedback = generateFeedback(feedbackRequest, conversation);

            completeCallFeedback(myClaim, myFeedback);

            return myFeedback;
        } catch (RuntimeException e) {
            releaseCallFeedbackClaim(memberId, callId);
            throw e;
        }
    }

    private Optional<FeedbackResponseDTOv1> findExistingCallFeedback(Long memberId, Long callId) {
        return transactionTemplate.execute(status ->
                feedbackRepository.findTopByMemberIdAndCallIdAndCompletedTrueOrderByCreatedAtDesc(memberId, callId)
                        .map(FeedbackConverter::toResponse));
    }

    private Feedback claimCallFeedback(Long memberId, Long callId) {
        try {
            return transactionTemplate.execute(status -> {
                callAccessValidator.validateParticipantForUpdate(callId, memberId);
                deleteExpiredCallFeedbackClaim(memberId, callId);

                if (feedbackRepository.existsByMemberIdAndCallIdAndCompletedTrue(memberId, callId)
                        || feedbackRepository.existsByMemberIdAndCallIdAndCompletedFalse(memberId, callId)) {
                    throw new BusinessException(ErrorStatus.DUPLICATE_REQUEST);
                }

                Feedback myFeedback = Feedback.callProcessing(memberId, callId);
                return feedbackRepository.save(myFeedback);
            });
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorStatus.DUPLICATE_REQUEST);
        }
    }

    private void deleteExpiredCallFeedbackClaim(Long memberId, Long callId) {
        LocalDateTime expiredBefore = LocalDateTime.now()
                .minusMinutes(CALL_FEEDBACK_PROCESSING_TIMEOUT_MINUTES);
        feedbackRepository.deleteByMemberIdAndCallIdAndCompletedFalseAndCreatedAtBefore(memberId, callId, expiredBefore);
    }

    private void completeCallFeedback(
            Feedback myClaim,
            FeedbackResponseDTOv1 myFeedback
    ) {
        transactionTemplate.executeWithoutResult(status -> {
            FeedbackConverter.complete(myClaim, myFeedback);
            feedbackRepository.save(myClaim);
        });
    }

    private void releaseCallFeedbackClaim(Long memberId, Long callId) {
        transactionTemplate.executeWithoutResult(status ->
                feedbackRepository.deleteByMemberIdAndCallIdAndCompletedFalse(memberId, callId));
    }

    private FeedbackResponseDTOv1 generateFeedback(FeedbackRequestDTOv1 feedbackRequest, List<ConversationTurn> conversation) {
        int maxAttempts = FEEDBACK_INVALID_RESPONSE_RETRY_COUNT + 1;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return generateFeedbackOnce(feedbackRequest, conversation);
            } catch (BusinessException e) {
                if (e.getBaseCode() != ErrorStatus.FEEDBACK_INVALID_RESPONSE || attempt == maxAttempts) {
                    throw e;
                }
            }
        }

        throw new BusinessException(ErrorStatus.FEEDBACK_INVALID_RESPONSE);
    }

    private FeedbackResponseDTOv1 generateFeedbackOnce(FeedbackRequestDTOv1 feedbackRequest, List<ConversationTurn> conversation) {
        Prompt prompt = createConversationFeedbackPrompt(feedbackRequest, conversation);
        FeedbackResponseDTOv1 result = entity(
                prompt,
                buildFlowCaseFilter(feedbackRequest.relationship()),
                FeedbackResponseDTOv1.class);

        FeedbackConverter.validateResponse(result);

        return result;
    }

    private List<ConversationTurn> getConversationFromChatMessages(Long memberId, Long roomId) {
        // 피드백은 대화 전체 흐름을 어느 정도 봐야 하므로 최근 메시지 100개를 기준으로 분석한다.
        Pageable limit = PageRequest.of(0, FEEDBACK_MESSAGE_LIMIT);
        List<ChatMessage> messages = new ArrayList<>(
                chatMessageRepository.findByChatRoomIdOrderByIdDesc(roomId, limit));

        // Repository는 최신순으로 가져오므로, LLM에는 실제 대화 순서인 오래된순으로 넘긴다.
        Collections.reverse(messages);

        List<ConversationTurn> conversation = messages.stream()
                .filter(message -> message.getMessageType() == MessageType.TEXT)
                .filter(message -> !message.isDeleted())
                .map(message -> new ConversationTurn(
                        // 현재 로그인한 사용자의 메시지는 me, 상대 메시지는 other로 고정한다.
                        message.isSentBy(memberId) ? "me" : "other",
                        message.getContent()))
                .toList();

        if (conversation.isEmpty()) {
            throw new BusinessException(ErrorStatus.FEEDBACK_CONVERSATION_EMPTY);
        }

        return conversation;
    }

    /**
     * LLM 응답을 지정한 Java 타입으로 변환해서 받는다.
     * /rag/feedback에서는 ConversationFeedbackResult record로 구조화된 피드백을 받을 때 사용한다.
     */
    public <T> T entity(Prompt prompt, Optional<String> filterExpressionAsOpt, Class<T> responseType) {
        return entity(chatClient, prompt, filterExpressionAsOpt, responseType);
    }

    private <T> T entity(ChatClient client, Prompt prompt, Optional<String> filterExpressionAsOpt, Class<T> responseType) {
        return prepareRequest(
                client,
                prompt,
                filterExpressionAsOpt)
                .call()
                .entity(responseType);
    }

    /**
     *
     * ChatClient 요청 공통 설정을 만든다.
     * filterExpression은 VectorStore 검색 대상을 좁히는 조건이다.
     */
    //ChatClient.ChatClientRequestSpec를 이렇게 쓰는이유는 중첩 인터페이스라서 그렇데
    // 이렇게 한이유가 ChatClient 전용 인터페이스여서 그렇데 만약 차 = Chatclient였으면 그내부에 엔진이라는 인터페이스가 있었을것임
    private ChatClient.ChatClientRequestSpec prepareRequest(
            ChatClient client,
            Prompt prompt,
            Optional<String> filterExpressionAsOpt
    )
    {
        Optional<String> validFilterExpression = filterExpressionAsOpt  // 해당하는 녀석들로 가져와 친구사이 vs 처음보는 사이
                .map(String::trim)
                .filter(filterExpression -> !filterExpression.isBlank())
                .filter(filterExpression -> !"string".equalsIgnoreCase(filterExpression));

        return validFilterExpression
                .map(filterExpression -> client.prompt(prompt).
                        advisors(advisorSpec ->
                                advisorSpec.param(VectorStoreDocumentRetriever.FILTER_EXPRESSION, filterExpression)))
                .orElse(client.prompt(prompt));
        // 여기서 Filter에 역할은 백터검색을 할떄 모든 문서를 대상으로하는게아닌 여기에 들어온값을 대상에 문서로 검색해
        // sql에 where절이랑 비슷하다고 보면됨

    }




    private static final String DEFAULT_SYSTEM_PROMPT = """  
            너는 사용자의 대화 능력을 향상시키는 “대화 피드백 코치”이다.

           목표는 사용자를 비난하는 것이 아니라, 사용자가 다음 대화에서 더 자연스럽고 배려 있게 말할 수 있도록 구체적인 피드백을 제공하는 것이다.

           입력으로는 다음 정보를 받는다.
           - 관계 유형: “처음 보는 사람” 또는 “친구”
           - 대화 상황
           - 대화 로그
           - 선택적으로 이전 피드백 기록

           분석 기준은 다음과 같다.

           [공통 기준]
           - 단답형 반응이 반복되는가?
           - 열린 질문을 사용하는가?
           - 상대 발화의 핵심 포인트를 잡아 이어가는가?
           - 상대의 감정이나 관심사를 무시하지 않는가?
           - 대화를 갑자기 끊거나 다른 주제로 넘기지 않는가?
           - 표현이 부담, 무시, 비난, 단정으로 느껴질 수 있는가?
           - 적절한 자기 이야기를 섞어 대화를 이어가는가?

           [대화 포인트]
           
           대화 포인트란 상대 발화에서 다음 대화로 이어갈 수 있는 중요한 단서이다.

           포인트 유형:
           - 관심사: 취미, 취향, 좋아하는 것, 자주 하는 활동
           - 경험: 최근에 한 일, 기억에 남는 일, 특별한 사건
           - 감정: 힘듦, 속상함, 기쁨, 기대, 걱정 등
           - 고민: 문제, 스트레스, 갈등, 부담
           - 관계: 친구, 가족, 연인, 팀원 등 타인과의 관계 언급
           - 변화: 새로 시작한 일, 최근 달라진 상태
           - 요청/기대: 도움, 이해, 조언, 경청을 바라는 표현

           [관계 유형별 기준]

           1. 처음 보는 사람
           대화 목표:
           - 어색함 줄이기
           - 부담 없는 분위기 만들기
           - 공통 관심사 찾기
           - 자연스럽게 대화 이어가기

           중요 포인트:
           - 관심사
           - 경험
           - 취향
           - 변화
           - 공통점
           - 가벼운 자기소개 단서

           좋은 반응:
           - 상대의 관심사나 경험을 잡아 부담 없는 질문을 한다.
           - 닫힌 질문만 반복하지 않고 열린 질문을 섞는다.
           - 짧은 반응 후 이어 질문을 한다.
           - 초반에는 너무 사적인 질문을 피한다.
           - 적절하게 자신의 경험도 조금 공유한다.

           아쉬운 반응:
           - “아 그렇군요”, “네”, “좋네요” 같은 단답만 반복한다.
           - 상대가 말한 관심사를 무시하고 다른 주제로 넘어간다.
           - 초반부터 연애, 돈, 가족 문제 등 사적인 질문을 한다.
           - 질문이 상대 답변과 연결되지 않는다.

           2. 친구
           대화 목표:
           - 감정 공감
           - 관계 유지
           - 편안한 반응
           - 고민 들어주기
           - 갈등 줄이기

           중요 포인트:
           - 감정
           - 고민
           - 서운함
           - 도움 요청
           - 좋은 소식
           - 관계 갈등
           - 반복되는 문제

           좋은 반응:
           - 친구의 감정을 먼저 인정한다.
           - 고민을 말했을 때 바로 조언하기보다 먼저 들어준다.
           - 핵심 사건이나 감정에 대해 이어 질문한다.
           - 도움을 원할 때는 어떤 도움을 원하는지 확인한다.
           - 장난이라도 상대를 깎아내리는 표현은 피한다.

           아쉬운 반응:
           - 친구의 감정을 무시하거나 가볍게 넘긴다.
           - 성급하게 해결책을 제시한다.
           - 친구 이야기를 바로 자기 이야기로 돌린다.
           - “너는 항상”, “너 원래”처럼 단정하거나 비난한다.
           - “됐어”, “몰라”처럼 대화를 끊는다.

           [피드백 작성 원칙]
           - 사용자를 비난하지 않는다.
           - 실제 대화 근거를 반드시 제시한다.
           - 문제점만 말하지 말고 개선 방향을 함께 제시한다.
           - 좋은 점도 최소 1개 이상 말한다.
           - 관계 유형에 맞게 판단한다.
           - 한 번의 대화만 보고 성향을 단정하지 않는다.
           - 반복 기록이 있을 때만 “반복되는 경향”이라고 표현한다.
           - 피드백은 구체적이고 바로 적용 가능해야 한다.

           [overallQuality 판단 기준]
           - GOOD: 대화 흐름이 자연스럽고, 상대의 말이나 감정을 적절히 받아주며, 뚜렷한 흐름 문제가 없는 경우
           - AMBIGUOUS: 대화는 유지되지만 단답, 얕은 반응, 놓친 대화 포인트, 약한 맥락 연결 등 개선 여지가 있는 경우
           - BAD: 상대가 불편할 가능성이 높은 표현, 감정 무시, 비난/압박, 부적절하게 사적인 질문, 명확한 대화 단절이 있는 경우

           [overallQuality 판정 원칙]
           - BAD는 명확한 근거가 있을 때만 선택한다.
           - GOOD은 단순히 문제가 없다는 이유만으로 선택하지 않는다. 자연스러운 이어가기와 적절한 반응이 있어야 한다.
           - 애매하면 AMBIGUOUS를 선택한다.
           - 서비스의 목적은 사용자를 혼내는 것이 아니라 다음 대화를 더 잘 이어가도록 돕는 것이다.

           [출력 형식]
           반드시 아래 형식을 따른다.

           1. 전체 요약
           이번 대화 흐름을 2~3문장으로 요약한다.

           2. 좋았던 점
           사용자가 잘한 점을 1~2개 제시한다.

           3. 아쉬웠던 점
           - 문제 유형:
           - 근거 문장:
           - 왜 아쉬운지:
           - 개선 방향:

           4. 놓친 대화 포인트
           - 상대가 꺼낸 중요한 포인트:
           - 사용자의 반응:
           - 왜 이어가면 좋았는지:
           - 추천 질문 또는 추천 반응:

           5. 개선 표현 예시
           - 기존 표현:
           - 더 나은 표현:
           - 관계 유형에 맞는 이유:

           6. 다음 대화 연습 목표
           다음 대화에서 실천할 수 있는 작은 목표 1개를 제시한다.
           """;


        private static Prompt createConversationFeedbackPrompt(FeedbackRequestDTOv1 feedbackBody,
                                                              List<ConversationTurn> conversation) {
        List<Message> messages = new ArrayList<>(); //LLM에게 보낼 시스템 프롬프트를  넣는곳 MESSAGE
        messages.add(new SystemMessage(DEFAULT_SYSTEM_PROMPT));  // LLM에게 페르소나 입력

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("다음은 사용자의 문자 대화 로그입니다. 전체 흐름을 보고 피드백해주세요.")
                .append(System.lineSeparator()).append(System.lineSeparator())
                .append("중요: 대화가 좋은지, 애매한지, 나쁜지는 직접 판단하세요. 사용자가 quality를 제공하지 않습니다.")
                .append(System.lineSeparator())
                .append("중요: 검색된 사례의 품질 평가를 그대로 가져오지 말고, 아래 입력 대화 자체를 기준으로 판단하세요.")
                .append(System.lineSeparator())
                .append("중요: 아래 대화에 실제로 없는 주제는 피드백에 쓰지 마세요. 예를 들어 입력에 나이 질문이 없으면 나이 문제를 언급하지 마세요.")
                .append(System.lineSeparator())
                .append("중요: 흐름 문제마다 실제 입력 대화의 어떤 문장이 근거인지 확인한 뒤 작성하세요.")
                .append(System.lineSeparator()).append(System.lineSeparator())
                .append("관계: ")
                .append(defaultValue(
                        feedbackBody.relationship() == null ? null : feedbackBody.relationship().getRagValue(),
                        "친구 or 처음만난 사이"
                ))
                .append(System.lineSeparator())
                .append("목표: ").append(defaultValue(feedbackBody.goal(), "자연스럽게 대화 이어가기"))
                .append(System.lineSeparator()).append(System.lineSeparator())
                .append("대화:")
                .append(System.lineSeparator());

        for (ConversationTurn turn : conversation) {
            userPrompt.append(turn.speaker())
                    .append(": ")
                    .append(turn.message())
                    .append(System.lineSeparator());
        }

        userPrompt.append(System.lineSeparator())
                .append("아래 필드 의미에 맞게 구조화해서 답변하세요.")
                .append(System.lineSeparator())
                .append("- overallQuality: GOOD, AMBIGUOUS, BAD 중 하나")
                .append(System.lineSeparator())
                .append("- overallFeedback: 전체 판정 이유. 문제가 있으면 '전반적으로 자연스럽다'라고 쓰지 말 것")
                .append(System.lineSeparator())
                .append("- strength: 잘한 점 목록. 같은 의미를 반복하지 말고, 구체적인 강점이 있으면 '첫 인사는 무난했습니다' 같은 일반 문장은 생략")
                .append(System.lineSeparator())
                .append("- flowProblem: 실제로 대화를 해치는 흐름 문제 목록. 단순히 더 깊게 물을 수 있었던 정도의 개선 가능성은 넣지 말 것")
                .append(System.lineSeparator())
                .append("  중요: 대화의 포인트를 찾아서 반영했다면 칭찬을 해주세요, 여기서 대화의 포인트란 대화에 언급된 단어입니다.")
                .append(System.lineSeparator())
                .append("  good 판정에서는 practicePoint, conversationPoints 를 비워야합니다. ")
                .append(System.lineSeparator())
                .append("- practicePoint: 다음에 연습할 한 가지 포인트")
                .append(System.lineSeparator()).append(System.lineSeparator())
                .append("판정 규칙:")
                .append(System.lineSeparator())
                .append("- flowProblem에는 실제로 대화를 해치는 문제만 넣고, 가벼운 개선 가능성은 practicePoint로 보낸다.")
                .append(System.lineSeparator())
                .append("- 사적인 질문, 압박, 평가, 대화 단절이 뚜렷하면 bad로 정한다.")
                .append(System.lineSeparator())
                .append("- 첫 인사만 괜찮고 이후 흐름이 끊겼다면 good이 아니다.")
                .append(System.lineSeparator())
                .append("conversationPoints에는 대화중에 집중해야할 포인트들을 넣어줬으면 해 ");

        messages.add(new UserMessage(userPrompt.toString()));

        Prompt.Builder promptBuilder = Prompt.builder().messages(messages);


        return promptBuilder.build();
    }

    /**
     * RAG 검색 대상을 대화 흐름 사례(type=flow_case)로 제한하고,
     * relationship/stage가 들어오면 같은 상황의 사례만 검색하도록 필터를 만든다.
     */


    private static Optional<String> buildFlowCaseFilter(@Nullable RelationShip relationship) {
        List<String> conditions = new ArrayList<>();
        conditions.add("type == 'conversation_standard'"); // 해당하는 녀석들로 가져와

        if (relationship != null) {// 관계 확정성 때문에  해당하는 관계 넣기

            conditions.add("relationship == '" + relationship.getRagValue() + "'");
        }


        return Optional.of(String.join(" && ", conditions)); //RAG쪽에서 LLM에게 넘겨줄 참고 문서를 줄임
    }

    /**
     * null 또는 빈 문자열이면 기본값을 사용한다.
     * Prompt 생성 시 relationship, stage, goal이 빠져도 문장이 깨지지 않게 한다.
     */

    private static String defaultValue(@Nullable String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }

        return value;
    }
}
