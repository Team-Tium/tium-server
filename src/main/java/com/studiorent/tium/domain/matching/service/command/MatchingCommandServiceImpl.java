package com.studiorent.tium.domain.matching.service.command;

import com.studiorent.tium.domain.matching.repository.MatchingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MatchingCommandServiceImpl implements MatchingCommandService {

    private final MatchingRepository matchingRepository;
}
