package ru.kaznacheev.notification.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class HeartbeatConfig {

    private final ApplicationProperties properties;

    @Bean
    public Flux<ServerSentEvent<Object>> heartbeatStream() {
        return Flux.interval(properties.getSseProperties().getHeartbeatInterval())
                .map(tick -> {
                    log.debug("Отправляем Heartbeat");
                    return ServerSentEvent.builder()
                            .event(properties.getEventsProperties().getHeartbeat().getEventName())
                            .comment(properties.getEventsProperties().getHeartbeat().getComment())
                            .build();
                })
                .share();
    }

}
