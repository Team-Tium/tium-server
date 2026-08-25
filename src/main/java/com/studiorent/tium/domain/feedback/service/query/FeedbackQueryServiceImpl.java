package com.studiorent.tium.domain.feedback.service.query;

import com.studiorent.tium.domain.feedback.repository.FeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedbackQueryServiceImpl implements FeedbackQueryService {

    private final FeedbackRepository feedbackRepository;
}
