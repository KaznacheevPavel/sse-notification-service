package ru.kaznacheev.notification.config;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@RequiredArgsConstructor
@Getter
public class ApplicationProperties {

    private final SseProperties sseProperties;
    private final EventsProperties eventsProperties;
    private final RedisRetryProperties redisRetryProperties;
    private final RedisTopicsProperties redisTopicsProperties;

    @Configuration
    @ConfigurationProperties(prefix = "application.sse")
    @Getter
    @Setter
    public static class SseProperties {
        private Duration heartbeatInterval;
        private Duration subscriberRemoveDelay;
    }

    @Configuration
    @ConfigurationProperties(prefix = "application.sse.events")
    @Getter
    @Setter
    public static class EventsProperties {
        private Event heartbeat;
        private Event start;
        private Event error;
        private Event notification;

        @Getter
        @Setter
        public static class Event {
            private String eventName;
            private String comment;
        }
    }

    @Configuration
    @ConfigurationProperties(prefix = "application.redis.retry")
    @Getter
    @Setter
    public static class RedisRetryProperties {
        private int maxAttempts;
        private Duration initialBackoff;
        private Duration maxBackoff;
        private double jitterFactor;
    }

    @Configuration
    @ConfigurationProperties(prefix = "application.redis.topics")
    @Getter
    @Setter
    public static class RedisTopicsProperties {
        private String userIdPlaceholder;
        private String userNotificationPattern;

        public String buildNotificationTopic(String userId) {
            return userNotificationPattern.replace(userIdPlaceholder, userId);
        }

    }

}
