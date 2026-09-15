package com.studiorent.tium.global.socket;

import com.studiorent.tium.global.security.CustomUserDetails;
import com.studiorent.tium.global.security.jwt.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern MEMBER_DESTINATION_PATTERN = Pattern.compile(
            "^" + Pattern.quote(SocketDestinations.MEMBER_SUB_PREFIX) + "/(\\d+)$");

    private final JwtProvider jwtProvider;
    private final List<SubscriptionAuthorizer> subscriptionAuthorizers;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticateConnect(accessor);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscribe(accessor);
        }

        return message;
    }

    private void authenticateConnect(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);

        if (!StringUtils.hasText(token) || !jwtProvider.validateAccessToken(token)) {
            throw new BadCredentialsException("Invalid access token");
        }

        CustomUserDetails userDetails = new CustomUserDetails(jwtProvider.getMemberId(token));
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        accessor.setUser(authentication);
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        Long memberId = getAuthenticatedMemberId(accessor.getUser());
        String destination = accessor.getDestination();

        if (!StringUtils.hasText(destination)) {
            throw new AccessDeniedException("Subscription destination is required");
        }

        if (canSubscribePersonalDestination(memberId, destination)) {
            return;
        }

        boolean supported = false;
        for (SubscriptionAuthorizer authorizer : subscriptionAuthorizers) {
            if (authorizer.supports(destination)) {
                supported = true;
                if (authorizer.canSubscribe(memberId, destination)) {
                    return;
                }
            }
        }

        if (!supported) {
            throw new AccessDeniedException("Unsupported subscription destination");
        }
        throw new AccessDeniedException("Forbidden subscription destination");
    }

    private Long getAuthenticatedMemberId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken authentication
                && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userDetails.getMemberId();
        }
        throw new AccessDeniedException("Authentication is required");
    }

    private boolean canSubscribePersonalDestination(Long memberId, String destination) {
        Matcher matcher = MEMBER_DESTINATION_PATTERN.matcher(destination);
        return matcher.matches() && memberId.equals(Long.valueOf(matcher.group(1)));
    }
}
