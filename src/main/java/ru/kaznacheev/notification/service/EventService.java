package ru.kaznacheev.notification.service;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface EventService {

    Flux<ServerSentEvent<Object>> createNotificationsStream(String userId);

}
