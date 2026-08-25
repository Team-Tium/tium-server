package com.studiorent.tium.domain.call.service.query;

import com.studiorent.tium.domain.call.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CallQueryServiceImpl implements CallQueryService {

    private final CallRepository callRepository;
}
