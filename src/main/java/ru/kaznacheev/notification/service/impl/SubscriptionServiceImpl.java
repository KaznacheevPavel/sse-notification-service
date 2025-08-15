package ru.kaznacheev.notification.service.impl;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;
import ru.kaznacheev.notification.config.ApplicationProperties;
import ru.kaznacheev.notification.model.NotificationDto;
import ru.kaznacheev.notification.model.Subscriber;
import ru.kaznacheev.notification.service.SubscriptionService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final Map<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private final ReactiveRedisTemplate<String, NotificationDto> redisTemplate;
    private final ApplicationProperties properties;

    @Override
    public Subscriber getOrCreateSubscriber(String userId) {
        return subscribers.computeIfAbsent(userId, this::createSubscriber);
    }

    @Override
    public void removeSubscriber(String userId, Subscriber subscriber) {
        Mono.delay(properties.getSseProperties().getSubscriberRemoveDelay())
                .subscribe(aLong -> {
                    if (subscriber.getSubscribersCount().get() == 0 && subscribers.remove(userId, subscriber)) {
                        subscriber.getRedisDisposable().dispose();
                        subscriber.getSink().tryEmitComplete();
                    }
                });
    }

    @PreDestroy
    public void shutdown() {
        subscribers.values().forEach(subscriber -> {
            subscriber.getRedisDisposable().dispose();
            subscriber.getSink().tryEmitComplete();
        });
        subscribers.clear();
    }

    private Subscriber createSubscriber(String userId) {
        String topic = properties.getRedisTopicsProperties().buildNotificationTopic(userId);
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().multicast().onBackpressureBuffer();

        Disposable redisSubscriber = redisTemplate.listenToChannel(topic)
                .map(ReactiveSubscription.Message::getMessage)
                .doOnNext(message -> {
                    sink.tryEmitNext(ServerSentEvent.builder()
                            .event(properties.getEventsProperties().getNotification().getEventName())
                            .data(message)
                            .comment(properties.getEventsProperties().getNotification().getComment())
                            .build());
                })
                .doOnError(error -> {
                    sink.tryEmitNext(ServerSentEvent.builder()
                            .event(properties.getEventsProperties().getError().getEventName())
                            .comment(properties.getEventsProperties().getError().getComment())
                            .build());
                    sink.tryEmitComplete();
                })
                .retryWhen(
                        Retry.backoff(properties.getRedisRetryProperties().getMaxAttempts(),
                                        properties.getRedisRetryProperties().getInitialBackoff())
                                .maxBackoff(properties.getRedisRetryProperties().getMaxBackoff())
                                .jitter(properties.getRedisRetryProperties().getJitterFactor()))
                .subscribe();
        return new Subscriber(sink, redisSubscriber);
    }

}
