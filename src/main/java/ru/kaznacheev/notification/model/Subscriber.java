package ru.kaznacheev.notification.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicInteger;

@AllArgsConstructor
@Getter
public class Subscriber {

    private final Sinks.Many<ServerSentEvent<Object>> sink;
    private final Disposable redisDisposable;
    private final AtomicInteger subscribersCount = new AtomicInteger(0);

}
