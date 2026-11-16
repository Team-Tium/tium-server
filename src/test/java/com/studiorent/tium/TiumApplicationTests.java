package com.studiorent.tium;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TiumApplicationTests {

    @Autowired ApplicationContext context;
    @Autowired ChatModel chatModel;
    @Value("${spring.ai.openai.chat.model}") String configuredModel;

    @Test
    void contextLoads() {
        assertThat(chatModel.getDefaultOptions().getModel()).isEqualTo(configuredModel);
        assertThat(context.getBeanNamesForType(EmbeddingModel.class)).isEmpty();
        assertThat(context.containsBean("retrievalAugmentationAdvisor")).isFalse();
        assertThat(context.containsBean("initEtlPipeline")).isFalse();
        assertThat(context.containsBean("vectorStore")).isFalse();
    }

}
