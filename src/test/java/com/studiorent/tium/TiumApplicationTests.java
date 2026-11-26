package com.studiorent.tium;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.converter.BeanOutputConverter;
import com.studiorent.tium.domain.feedback.dto.analysis.ConversationFeedbackEvaluation;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void 모델_JSON_변환_실패시_응답_원문을_로그에_남기지_않는다() {
        String privateResponse = "PRIVATE-CONVERSATION-MUST-NOT-BE-LOGGED";
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            var converter = new BeanOutputConverter<>(ConversationFeedbackEvaluation.class);
            assertThatThrownBy(() -> converter.convert(privateResponse)).isInstanceOf(RuntimeException.class);
            assertThat(appender.list).noneMatch(event -> event.getFormattedMessage().contains(privateResponse));
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

}
