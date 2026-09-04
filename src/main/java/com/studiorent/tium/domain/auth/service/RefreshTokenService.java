package com.studiorent.tium.domain.auth.service;

import com.studiorent.tium.domain.auth.entity.RefreshToken;
import com.studiorent.tium.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public void save(Long memberId, String rawToken, LocalDateTime expiresAt) {
        String hash = hash(rawToken);

        refreshTokenRepository.findByMemberId(memberId)
                .ifPresentOrElse(
                        existing -> existing.rotate(hash, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.builder()
                                .memberId(memberId)
                                .tokenHash(hash)
                                .expiresAt(expiresAt)
                                .build())
                );
    }

    @Transactional(readOnly = true)
    public boolean matches(Long memberId, String rawToken) {
        return refreshTokenRepository.findByMemberId(memberId)
                .map(token -> token.matches(hash(rawToken)))
                .orElse(false);
    }

    @Transactional
    public void delete(Long memberId) {
        refreshTokenRepository.deleteByMemberId(memberId);
    }

    private String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
