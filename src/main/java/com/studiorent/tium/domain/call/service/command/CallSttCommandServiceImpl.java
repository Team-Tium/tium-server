package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import com.studiorent.tium.domain.call.entity.Call;
import com.studiorent.tium.domain.call.entity.CallSttJob;
import com.studiorent.tium.domain.call.entity.enums.CallFeedbackStatus;
import com.studiorent.tium.domain.call.repository.CallSttJobRepository;
import com.studiorent.tium.domain.call.repository.CallSttSegmentRepository;
import com.studiorent.tium.domain.call.service.CallAccessValidator;
import com.studiorent.tium.domain.call.service.CallSttAsyncProcessor;
import com.studiorent.tium.domain.call.service.CallTranscriptionService;
import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;

@Service
public class CallSttCommandServiceImpl implements CallSttCommandService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final CallAccessValidator callAccessValidator;
    private final CallTranscriptionService callTranscriptionService;
    private final CallSttAsyncProcessor callSttAsyncProcessor;
    private final CallSttJobRepository callSttJobRepository;
    private final CallSttSegmentRepository callSttSegmentRepository;
    private final FeedbackRepository feedbackRepository;
    private final TransactionTemplate transactionTemplate;

    public CallSttCommandServiceImpl(
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

    @Override
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
