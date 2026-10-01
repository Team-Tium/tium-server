package com.studiorent.tium.domain.call.service;

import com.studiorent.tium.domain.call.entity.CallSttJob;
import com.studiorent.tium.domain.call.entity.CallSttSegment;
import com.studiorent.tium.domain.call.repository.CallSttJobRepository;
import com.studiorent.tium.domain.call.repository.CallSttSegmentRepository;
import com.studiorent.tium.domain.call.service.CallTranscriptionService.AudioTranscriptSegment;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CallSttAsyncProcessor {

    private final CallTranscriptionService callTranscriptionService;
    private final CallSttJobRepository callSttJobRepository;
    private final CallSttSegmentRepository callSttSegmentRepository;
    private final PlatformTransactionManager transactionManager;

    @Async("callSttTaskExecutor")
    public void process(Long jobId, String attemptId, Path tempFile, long offsetMs) {
        try {
            List<AudioTranscriptSegment> transcriptSegments =
                    callTranscriptionService.transcribeSegments(tempFile, offsetMs);

            if (transcriptSegments.isEmpty()) {
                markFailedIfCurrent(jobId, attemptId, ErrorStatus.FEEDBACK_CONVERSATION_EMPTY.getCode());
                return;
            }

            completeIfCurrent(jobId, attemptId, transcriptSegments);
        } catch (BusinessException e) {
            markFailedIfCurrent(jobId, attemptId, e.getBaseCode().getCode());
        } catch (RuntimeException e) {
            log.error("Unexpected call STT async failure: jobId={}", jobId, e);
            markFailedIfCurrent(jobId, attemptId, ErrorStatus.FEEDBACK_TRANSCRIPTION_FAILED.getCode());
        } finally {
            callTranscriptionService.deleteTempFile(tempFile);
        }
    }

    private void completeIfCurrent(
            Long jobId,
            String attemptId,
            List<AudioTranscriptSegment> transcriptSegments
    ) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CallSttJob job = findJob(jobId);
            if (!job.isCurrentAttempt(attemptId)) {
                return;
            }

            List<CallSttSegment> segments = IntStream.range(0, transcriptSegments.size())
                    .mapToObj(index -> toEntity(job.getCallId(), job.getSpeakerUserId(), index, transcriptSegments.get(index)))
                    .toList();

            callSttSegmentRepository.deleteByCallIdAndSpeakerUserId(job.getCallId(), job.getSpeakerUserId());
            callSttSegmentRepository.saveAll(segments);
            job.complete();
        });
    }

    private void markFailedIfCurrent(Long jobId, String attemptId, String failedReason) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CallSttJob job = findJob(jobId);
            if (job.isCurrentAttempt(attemptId)) {
                job.fail(failedReason);
            }
        });
    }

    private CallSttJob findJob(Long jobId) {
        return callSttJobRepository.findById(jobId)
                .orElseThrow(() -> new BusinessException(ErrorStatus.CALL_STT_NOT_FOUND));
    }

    private static CallSttSegment toEntity(
            Long callId,
            Long memberId,
            int index,
            AudioTranscriptSegment segment
    ) {
        return CallSttSegment.create(
                callId,
                memberId,
                index + 1,
                Math.round(segment.startSeconds() * 1000),
                Math.round(segment.endSeconds() * 1000),
                segment.text());
    }
}
