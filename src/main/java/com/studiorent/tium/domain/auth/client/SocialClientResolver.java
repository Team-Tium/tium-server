package com.studiorent.tium.domain.auth.client;

import com.studiorent.tium.domain.member.entity.enums.Provider;
import com.studiorent.tium.global.exception.BusinessException;
import com.studiorent.tium.global.response.code.status.ErrorStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class SocialClientResolver {

    private final Map<Provider, SocialClient> clients = new EnumMap<>(Provider.class);

    public SocialClientResolver(List<SocialClient> socialClients) {
        socialClients.forEach(client -> clients.put(client.getProvider(), client));
    }

    public SocialClient resolve(Provider provider) {
        SocialClient client = clients.get(provider);

        if (client == null) {
            throw new BusinessException(ErrorStatus.AUTH_UNSUPPORTED_PROVIDER);
        }
        return client;
    }
}
