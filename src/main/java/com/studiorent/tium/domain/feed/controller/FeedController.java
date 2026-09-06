package com.studiorent.tium.domain.feed.controller;

import com.studiorent.tium.domain.feed.dto.FeedRequestDTO;
import com.studiorent.tium.domain.feed.dto.FeedResponseDTO;
import com.studiorent.tium.domain.feed.service.command.FeedCommandService;
import com.studiorent.tium.domain.feed.service.query.FeedQueryService;
import com.studiorent.tium.global.response.ApiResponse;
import com.studiorent.tium.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedCommandService feedCommandService;
    private final FeedQueryService feedQueryService;

    @PostMapping
    public ApiResponse<FeedResponseDTO.FeedResultDTO> createFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FeedRequestDTO.FeedDTO request) {

        return ApiResponse.onSuccess(feedCommandService.createFeed(userDetails.getMemberId(), request));
    }

    @GetMapping("/{feedId}")
    public ApiResponse<FeedResponseDTO.FeedResultDTO> getFeed(@PathVariable Long feedId) {
        return ApiResponse.onSuccess(feedQueryService.getFeed(feedId));
    }

    @PatchMapping("/{feedId}")
    public ApiResponse<FeedResponseDTO.FeedResultDTO> updateFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long feedId,
            @Valid @RequestBody FeedRequestDTO.FeedDTO request) {

        return ApiResponse.onSuccess(feedCommandService.updateFeed(userDetails.getMemberId(), feedId, request));
    }

    @DeleteMapping("/{feedId}")
    public ApiResponse<Void> deleteFeed(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long feedId) {

        feedCommandService.deleteFeed(userDetails.getMemberId(), feedId);

        return ApiResponse.onSuccess(null);
    }

    @PostMapping("/heart")
    public ApiResponse<FeedResponseDTO.FeedHeartResultDTO> toggleHeart(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody FeedRequestDTO.FeedHeartDTO request) {

        return ApiResponse.onSuccess(feedCommandService.toggleHeart(userDetails.getMemberId(), request));
    }

    @GetMapping("/heart")
    public ApiResponse<List<FeedResponseDTO.FeedHeartHistoryDTO>> getMyFeedHeartHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ApiResponse.onSuccess(feedQueryService.getMyFeedHeartHistory(userDetails.getMemberId()));
    }
}
