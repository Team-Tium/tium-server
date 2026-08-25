package com.studiorent.tium.domain.feed.service.command;

import com.studiorent.tium.domain.feed.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedCommandServiceImpl implements FeedCommandService {

    private final FeedRepository feedRepository;
}
