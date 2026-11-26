package com.studiorent.tium.global.config;

import com.openai.client.OpenAIClient;
import com.studiorent.tium.domain.feedback.service.analysis.BoundedOpenAiHttpClient;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** SDK의 무제한 Retry-After 대기 대신 시간 예산을 지키는 HTTP 재시도를 사용한다. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
@EnableConfigurationProperties({OpenAiCommonProperties.class, OpenAiChatProperties.class})
public class FeedbackAiConfig {
    @Bean(destroyMethod = "close")
    public OpenAIClient feedbackOpenAiClient(OpenAiCommonProperties common, OpenAiChatProperties chat,
            @Value("${tium.feedback.http-retry-max-delay:500ms}") Duration retryMaxDelay) {
        var properties = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, chat);
        // 실제 재시도는 아래 전송 계층에서 처리한다. SDK 중첩 재시도는 끈다.
        var client = OpenAiSetup.setupSyncClient(properties.getBaseUrl(), properties.getApiKey(),
                properties.getCredential(), properties.getMicrosoftDeploymentName(),
                properties.getMicrosoftFoundryServiceVersion(), properties.getOrganizationId(),
                properties.isMicrosoftFoundry(), properties.isGitHubModels(), properties.getModel(),
                properties.getTimeout(), 0, properties.getProxy(), properties.getCustomHeaders());
        try {
            return client.withOptions(options -> options.httpClient(new BoundedOpenAiHttpClient(
                    options.build().httpClient(), properties.getTimeout(), properties.getMaxRetries(), retryMaxDelay)));
        } catch (RuntimeException failure) {
            client.close();
            throw failure;
        }
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAIClient feedbackOpenAiClient, OpenAiChatProperties chat,
            ToolCallingManager tools, ObjectProvider<ObservationRegistry> observations,
            ObjectProvider<ChatModelObservationConvention> convention,
            ObjectProvider<ToolExecutionEligibilityPredicate> eligibility) {
        var model = OpenAiChatModel.builder().openAiClient(feedbackOpenAiClient)
                .openAiClientAsync(feedbackOpenAiClient.async()).options(chat.toOptions())
                .toolCallingManager(tools)
                .observationRegistry(observations.getIfUnique(() -> ObservationRegistry.NOOP))
                .toolExecutionEligibilityPredicate(eligibility.getIfUnique(DefaultToolExecutionEligibilityPredicate::new))
                .build();
        convention.ifAvailable(model::setObservationConvention);
        return model;
    }
}
