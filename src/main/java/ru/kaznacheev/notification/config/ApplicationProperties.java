package ru.kaznacheev.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ApplicationProperties {

    @Configuration
    @ConfigurationProperties(prefix = "app.sse")
    @Getter
    @Setter
    public static class SseProperties {

        private Duration heartbeatInterval;
        private Duration subscriberRemoveDelay;
        private int redisMaxRetries;
        private Duration redisRetryBackoff;
        private Duration redisMaxBackoff;
        private int maxBufferSize;
        private String redisChannelPrefix;

    }

}
