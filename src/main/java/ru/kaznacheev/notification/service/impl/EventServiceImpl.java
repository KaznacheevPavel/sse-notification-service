package ru.kaznacheev.notification.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.ReactiveRedisMessageListenerContainer;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;
import ru.kaznacheev.notification.config.ApplicationProperties;
import ru.kaznacheev.notification.model.Subscriber;
import ru.kaznacheev.notification.service.EventService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final ReactiveRedisMessageListenerContainer messageListenerContainer;
    private final Map<String, Subscriber> sinks = new ConcurrentHashMap<>();
    private final ApplicationProperties.SseProperties properties;

    @Override
    public Flux<ServerSentEvent<Object>> createNotificationsStream(String userId) {
        Subscriber subscriber = sinks.computeIfAbsent(userId, this::createSubscriber);
        subscriber.getSubscribersCount().incrementAndGet();

        Flux<ServerSentEvent<Object>> heartbeatStream = Flux.interval(properties.getHeartbeatInterval())
                .map(tick -> ServerSentEvent.builder()
                        .event("PING")
                        .comment("heartbeat")
                        .build());

        return subscriber.getSink().asFlux()
                .mergeWith(heartbeatStream)
                .startWith(ServerSentEvent.builder()
                        .event("CONNECT")
                        .comment("connect")
                        .build())
                .doFinally(signalType -> {
                    if (subscriber.getSubscribersCount().decrementAndGet() == 0) {
                        removeSubscriber(userId, subscriber);
                    }
                });
    }

    private void removeSubscriber(String userId, Subscriber subscriber) {
        Mono.delay(properties.getSubscriberRemoveDelay())
                .subscribe(aLong -> {
                    if (subscriber.getSubscribersCount().get() == 0 && sinks.remove(userId, subscriber)) {
                        subscriber.getRedisDisposable().dispose();
                        subscriber.getSink().tryEmitComplete();
                    }
                });
    }

    private Subscriber createSubscriber(String userId) {
        ChannelTopic topic = new ChannelTopic(properties.getRedisChannelPrefix() + userId);
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().multicast().onBackpressureBuffer();

        Disposable redisSubscriber = messageListenerContainer.receive(topic)
                .map(ReactiveSubscription.Message::getMessage)
                .doOnNext(message -> {
                    sink.tryEmitNext(ServerSentEvent.builder()
                            .event("EVENT")
                            .data(message)
                            .build());
                })
                .doOnError(error -> {
                    sink.tryEmitNext(ServerSentEvent.builder()
                                    .event("ERROR")
                                    .comment("Internal error")
                            .build());
                })
                .retryWhen(
                        Retry.backoff(properties.getRedisMaxRetries(), properties.getRedisRetryBackoff())
                                .maxBackoff(properties.getRedisMaxBackoff())
                                .jitter(0.5))
                .subscribe();
        return new Subscriber(sink, redisSubscriber);
    }

}
