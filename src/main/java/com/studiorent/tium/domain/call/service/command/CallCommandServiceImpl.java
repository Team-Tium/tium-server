package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.repository.CallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CallCommandServiceImpl implements CallCommandService {

    private final CallRepository callRepository;
}
