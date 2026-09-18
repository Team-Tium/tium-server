package com.studiorent.tium.domain.feedback.service.command;

import com.studiorent.tium.domain.feedback.dto.v1.FeedbackRequestDTOv1;
import com.studiorent.tium.domain.feedback.dto.v1.FeedbackResponseDTOv1;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;

public interface FeedbackCommandService {
     FeedbackResponseDTOv1 FeedbackCreate(Long roomId, FeedbackRequestDTOv1 requestDTOv1);

}
