package com.studiorent.tium.domain.matching.service.query;

import com.studiorent.tium.domain.matching.repository.MatchingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchingQueryServiceImpl implements MatchingQueryService {

    private final MatchingRepository matchingRepository;
}
