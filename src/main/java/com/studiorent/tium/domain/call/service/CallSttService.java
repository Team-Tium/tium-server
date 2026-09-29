package com.studiorent.tium.domain.call.service;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.CallSttJob;
import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.call.entity.MemberCall;
import com.studiorent.tium.domain.call.entity.enums.CallFeedbackStatus;
import com.studiorent.tium.domain.call.entity.enums.CallSttJobStatus;
import com.studiorent.tium.domain.call.repository.CallSttJobRepository;
import com.studiorent.tium.domain.call.repository.CallSttSegmentRepository;
import com.studiorent.tium.domain.feedback.dto.ConversationTurn;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CallSttService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CallAccessValidator callAccessValidator;
    private final CallTranscriptionService callTranscriptionService;
    private final CallSttAsyncProcessor callSttAsyncProcessor;
    private final CallSttJobRepository callSttJobRepository;
    private final CallSttSegmentRepository callSttSegmentRepository;
    private final FeedbackRepository feedbackRepository;
    private final TransactionTemplate transactionTemplate;

    public CallSttService(
            CallAccessValidator callAccessValidator,
            CallTranscriptionService callTranscriptionService,
            CallSttAsyncProcessor callSttAsyncProcessor,
            CallSttJobRepository callSttJobRepository,
            CallSttSegmentRepository callSttSegmentRepository,
            FeedbackRepository feedbackRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.callAccessValidator = callAccessValidator;
        this.callTranscriptionService = callTranscriptionService;
        this.callSttAsyncProcessor = callSttAsyncProcessor;
        this.callSttJobRepository = callSttJobRepository;
        this.callSttSegmentRepository = callSttSegmentRepository;
        this.feedbackRepository = feedbackRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public CallSttResponseDTO.SaveResult saveStt(
            Long memberId,
            Long callId,
            MultipartFile file,
            String startedAt
    ) {
        Call call = callAccessValidator.validateParticipant(callId, memberId);
        validateSttWritable(callId);
        long offsetMs = calculateOffsetMs(call, startedAt);
        Path tempFile = callTranscriptionService.prepareTempFile(file);
        String attemptId = UUID.randomUUID().toString();

        CallSttJob job = transactionTemplate.execute(status -> {
            CallSttJob sttJob = callSttJobRepository.findByCallIdAndSpeakerUserId(callId, memberId)
                    .map(existingJob -> {
                        existingJob.restart(attemptId);
                        return existingJob;
                    })
                    .orElseGet(() -> CallSttJob.analyzing(callId, memberId, attemptId));
            callSttSegmentRepository.deleteByCallIdAndSpeakerUserId(callId, memberId);
            return callSttJobRepository.save(sttJob);
        });

        boolean submitted = false;
        try {
            callSttAsyncProcessor.process(job.getId(), attemptId, tempFile, offsetMs);
            submitted = true;
        } finally {
            if (!submitted) {
                callTranscriptionService.deleteTempFile(tempFile);
            }
        }

        return new CallSttResponseDTO.SaveResult(
                callId,
                memberId,
                CallFeedbackStatus.ANALYZING,
                "대화 내용을 분석 중입니다.");
    }

    private void validateSttWritable(Long callId) {
        if (feedbackRepository.existsByCallIdAndCompletedTrue(callId)) {
            throw new BusinessException(ErrorStatus.CALL_STT_ALREADY_USED);
        }
    }

    @Transactional(readOnly = true)
    public CallSttResponseDTO.SegmentList findStt(Long memberId, Long callId) {
        callAccessValidator.validateParticipant(callId, memberId);

        List<CallSttSegment> segments = findSegments(callId);
        return new CallSttResponseDTO.SegmentList(
                callId,
                segments.stream()
                        .map(segment -> toResponse(memberId, segment))
                        .toList());
    }

    @Transactional(readOnly = true)
    public CallSttResponseDTO.Status findSttStatus(Long memberId, Long callId) {
        Call call = callAccessValidator.validateParticipant(callId, memberId);
        return getFeedbackStatus(memberId, call);
    }

    public CallSttResponseDTO.Status getFeedbackStatus(Long memberId, Call call) {
        Long callId = call.getId();
        Long opponentId = getOpponentId(call, memberId);

        boolean mySttSaved = callSttSegmentRepository.existsByCallIdAndSpeakerUserId(callId, memberId);
        boolean otherSttSaved = callSttSegmentRepository.existsByCallIdAndSpeakerUserId(callId, opponentId);
        Optional<CallSttJob> myJob = callSttJobRepository.findByCallIdAndSpeakerUserId(callId, memberId);
        Optional<CallSttJob> otherJob = callSttJobRepository.findByCallIdAndSpeakerUserId(callId, opponentId);
        boolean feedbackCreated = feedbackRepository.existsByMemberIdAndCallIdAndCompletedTrue(memberId, callId);
        boolean feedbackGenerating = feedbackRepository.existsByMemberIdAndCallIdAndCompletedFalse(memberId, callId);
        CallFeedbackStatus status = resolveStatus(
                mySttSaved,
                otherSttSaved,
                myJob,
                otherJob,
                feedbackCreated,
                feedbackGenerating);
        boolean feedbackReady = status == CallFeedbackStatus.READY;

        return new CallSttResponseDTO.Status(
                callId,
                status,
                mySttSaved,
                otherSttSaved,
                feedbackReady,
                feedbackCreated,
                resolveMissingRecordingOwner(mySttSaved, otherSttSaved),
                resolveFailedReason(myJob, otherJob),
                buildStatusMessage(status, mySttSaved, otherSttSaved));
    }

    @Transactional(readOnly = true)
    public List<ConversationTurn> findConversationForFeedback(Long memberId, Long callId) {
        callAccessValidator.validateParticipant(callId, memberId);

        List<CallSttSegment> segments = findSegments(callId);
        long speakerCount = segments.stream()
                .map(CallSttSegment::getSpeakerUserId)
                .distinct()
                .count();

        if (speakerCount < 2) {
            throw new BusinessException(ErrorStatus.CALL_STT_NOT_READY);
        }

        return segments.stream()
                .map(segment -> new ConversationTurn(
                        toSpeaker(memberId, segment.getSpeakerUserId()),
                        segment.getText()))
                .toList();
    }

    private List<CallSttSegment> findSegments(Long callId) {
        List<CallSttSegment> segments = callSttSegmentRepository.findByCallIdOrderByStartedAtMsAscIdAsc(callId);
        if (segments.isEmpty()) {
            throw new BusinessException(ErrorStatus.CALL_STT_NOT_FOUND);
        }

        return segments;
    }

    private static CallSttResponseDTO.Segment toResponse(Long memberId, CallSttSegment segment) {
        return new CallSttResponseDTO.Segment(
                toSpeaker(memberId, segment.getSpeakerUserId()),
                segment.getStartedAtMs(),
                segment.getEndedAtMs(),
                segment.getText());
    }

    private static String toSpeaker(Long memberId, Long speakerUserId) {
        return speakerUserId.equals(memberId) ? "ME" : "OTHER";
    }

    private static Long getOpponentId(Call call, Long memberId) {
        return call.getParticipants().stream()
                .map(MemberCall::getMemberId)
                .filter(participantId -> !participantId.equals(memberId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorStatus.BAD_REQUEST));
    }

    private static CallFeedbackStatus resolveStatus(
            boolean mySttSaved,
            boolean otherSttSaved,
            Optional<CallSttJob> myJob,
            Optional<CallSttJob> otherJob,
            boolean feedbackCreated,
            boolean feedbackGenerating
    ) {
        if (feedbackCreated) {
            return CallFeedbackStatus.DONE;
        }

        if (feedbackGenerating) {
            return CallFeedbackStatus.GENERATING;
        }

        if (isAnalyzing(myJob) || isAnalyzing(otherJob)) {
            return CallFeedbackStatus.ANALYZING;
        }

        if (isFailed(myJob) || isFailed(otherJob)) {
            return CallFeedbackStatus.FAILED;
        }

        if (mySttSaved && otherSttSaved) {
            return CallFeedbackStatus.READY;
        }

        return CallFeedbackStatus.WAITING_RECORDING;
    }

    private static boolean isAnalyzing(Optional<CallSttJob> job) {
        return job.map(CallSttJob::getStatus)
                .filter(status -> status == CallSttJobStatus.ANALYZING)
                .isPresent();
    }

    private static boolean isFailed(Optional<CallSttJob> job) {
        return job.map(CallSttJob::getStatus)
                .filter(status -> status == CallSttJobStatus.FAILED)
                .isPresent();
    }

    private static String resolveFailedReason(Optional<CallSttJob> myJob, Optional<CallSttJob> otherJob) {
        return myJob
                .filter(job -> job.getStatus() == CallSttJobStatus.FAILED)
                .map(CallSttJob::getFailedReason)
                .or(() -> otherJob
                        .filter(job -> job.getStatus() == CallSttJobStatus.FAILED)
                        .map(CallSttJob::getFailedReason))
                .orElse(null);
    }

    private static String resolveMissingRecordingOwner(boolean mySttSaved, boolean otherSttSaved) {
        if (mySttSaved && otherSttSaved) {
            return null;
        }
        if (!mySttSaved && !otherSttSaved) {
            return "BOTH";
        }
        if (!mySttSaved) {
            return "ME";
        }
        return "OTHER";
    }

    private static String buildStatusMessage(
            CallFeedbackStatus status,
            boolean mySttSaved,
            boolean otherSttSaved
    ) {
        if (status == CallFeedbackStatus.DONE) {
            return "피드백 생성이 완료되었습니다.";
        }
        if (status == CallFeedbackStatus.GENERATING) {
            return "피드백을 생성하는 중입니다.";
        }
        if (status == CallFeedbackStatus.READY) {
            return "대화 내용 분석이 완료되었습니다.";
        }
        if (status == CallFeedbackStatus.ANALYZING) {
            return "대화 내용을 분석 중입니다.";
        }
        if (status == CallFeedbackStatus.FAILED) {
            return "대화 내용 분석에 실패했습니다.";
        }
        if (!mySttSaved && otherSttSaved) {
            return "통화 녹음파일을 분석 중입니다.";
        }
        return "상대방 통화 녹음파일을 기다리는 중입니다.";
    }

    private static long calculateOffsetMs(Call call, String startedAt) {
        if (startedAt == null || startedAt.isBlank()) {
            return 0L;
        }

        try {
            Instant recordingStartedAt = Instant.parse(startedAt);
            Instant callStartedAt = call.getStartAt()
                    .atZone(SERVICE_ZONE)
                    .toInstant();

            return Duration.between(callStartedAt, recordingStartedAt).toMillis();
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorStatus.BAD_REQUEST);
        }
    }
}
