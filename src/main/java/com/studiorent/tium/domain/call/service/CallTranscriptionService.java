package com.studiorent.tium.domain.call.service;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.audio.AudioResponseFormat;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.audio.transcriptions.TranscriptionCreateResponse;
import com.openai.models.audio.transcriptions.TranscriptionSegment;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class CallTranscriptionService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "flac", "mp3", "mp4", "mpeg", "mpga", "m4a", "ogg", "wav", "webm");

    private final OpenAIClient openAIClient;
    private final String transcriptionModel;
    private final String transcriptionLanguage;

    public CallTranscriptionService(
            @Value("${spring.ai.openai.api-key}") String apiKey,
            @Value("${spring.ai.openai.audio.transcription.model:whisper-1}") String transcriptionModel,
            @Value("${spring.ai.openai.audio.transcription.language:ko}") String transcriptionLanguage
    ) {
        this.openAIClient = OpenAIOkHttpClient.builder()
                .apiKey(apiKey)
                .build();
        this.transcriptionModel = transcriptionModel;
        this.transcriptionLanguage = transcriptionLanguage;
    }

    public List<AudioTranscriptSegment> transcribeSegments(MultipartFile audioFile, long offsetMs) {
        Path tempFile = prepareTempFile(audioFile);

        try {
            return transcribeSegments(tempFile, offsetMs);
        } finally {
            deleteTempFile(tempFile);
        }
    }

    public Path prepareTempFile(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new BusinessException(ErrorStatus.FEEDBACK_AUDIO_EMPTY);
        }

        String extension = getSupportedExtension(audioFile.getOriginalFilename());

        try {
            Path tempFile = Files.createTempFile("tium-call-recording-", "." + extension);
            try (InputStream inputStream = audioFile.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            log.info("Prepared call recording temp file: originalFilename={}, contentType={}, multipartSize={}, tempSize={}, magic={}",
                    audioFile.getOriginalFilename(),
                    audioFile.getContentType(),
                    audioFile.getSize(),
                    Files.size(tempFile),
                    readMagic(tempFile));
            return tempFile;
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to prepare call recording temp file: filename={}", audioFile.getOriginalFilename(), e);
            throw new BusinessException(ErrorStatus.FEEDBACK_TRANSCRIPTION_FAILED);
        }
    }

    public List<AudioTranscriptSegment> transcribeSegments(Path audioPath, long offsetMs) {
        return transcribeFileSegments(audioPath, offsetMs / 1000.0);
    }

    private List<AudioTranscriptSegment> transcribeFileSegments(Path audioPath, double offsetSeconds) {
        try {
            TranscriptionCreateParams params = TranscriptionCreateParams.builder()
                    .file(audioPath)
                    .model(transcriptionModel)
                    .responseFormat(AudioResponseFormat.VERBOSE_JSON)
                    .language(transcriptionLanguage)
                    .temperature(0.0)
                    .addTimestampGranularity(TranscriptionCreateParams.TimestampGranularity.SEGMENT)
                    .build();

            TranscriptionCreateResponse response = openAIClient.audio().transcriptions().create(params);
            if (!response.isVerbose()) {
                log.warn("OpenAI transcription response was not verbose: model={}, file={}", transcriptionModel, audioPath);
                throw new BusinessException(ErrorStatus.FEEDBACK_TRANSCRIPTION_FAILED);
            }

            return response.asVerbose().segments()
                    .orElse(List.of())
                    .stream()
                    .map(segment -> toAudioTranscriptSegment(segment, offsetSeconds))
                    .filter(segment -> !segment.text().isBlank())
                    .toList();
        } catch (RuntimeException e) {
            log.warn("OpenAI transcription failed: model={}, language={}, file={}",
                    transcriptionModel, transcriptionLanguage, audioPath, e);
            throw new BusinessException(ErrorStatus.FEEDBACK_TRANSCRIPTION_FAILED);
        }
    }

    private static AudioTranscriptSegment toAudioTranscriptSegment(
            TranscriptionSegment segment,
            double offsetSeconds
    ) {
        return new AudioTranscriptSegment(
                segment.start() + offsetSeconds,
                segment.end() + offsetSeconds,
                segment.text().trim()
        );
    }

    private static String getSupportedExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BusinessException(ErrorStatus.FEEDBACK_AUDIO_UNSUPPORTED);
        }

        String extension = filename.substring(filename.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);

        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(ErrorStatus.FEEDBACK_AUDIO_UNSUPPORTED);
        }

        return extension;
    }

    private static String readMagic(Path file) throws IOException {
        byte[] bytes;
        try (InputStream inputStream = Files.newInputStream(file)) {
            bytes = inputStream.readNBytes(16);
        }

        return HexFormat.of().formatHex(bytes);
    }

    public void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(tempFile);
        } catch (IOException ignored) {
        }
    }

    public record AudioTranscriptSegment(
            double startSeconds,
            double endSeconds,
            String text
    ) {
    }
}
