package com.studiorent.tium.domain.call.service.command;

import com.studiorent.tium.domain.call.dto.CallSttResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface CallSttCommandService {

    CallSttResponseDTO.SaveResult saveStt(
            Long memberId,
            Long callId,
            MultipartFile file,
            String startedAt
    );
}
