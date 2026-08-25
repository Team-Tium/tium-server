package com.studiorent.tium.domain.feed.service.query;

import com.studiorent.tium.domain.feed.repository.FeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FeedQueryServiceImpl implements FeedQueryService {

    private final FeedRepository feedRepository;
}
