package ru.kaznacheev.notification.service.impl;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
                    log.debug("Количество = {}", subscriber.getSubscribersCount().get());
                    if (subscriber.getSubscribersCount().get() == 0 && subscribers.remove(userId, subscriber)) {
                        log.info("Удаляем подписчика для userId = {}", userId);
                        subscriber.getRedisDisposable().dispose();
                        subscriber.getSink().tryEmitComplete();
                    } else {
                        log.debug("Отмена удаления для userId = {}", userId);
                    }
                });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Закрываем SSE сервис, отключаем {} подписчиков", subscribers.size());
        subscribers.values().forEach(subscriber -> {
            subscriber.getRedisDisposable().dispose();
            subscriber.getSink().tryEmitComplete();
        });
        subscribers.clear();
    }

    private Subscriber createSubscriber(String userId) {
        log.info("Создаем нового подписчика для userId = {}", userId);
        String topic = properties.getRedisTopicsProperties().buildNotificationTopic(userId);
        Sinks.Many<ServerSentEvent<Object>> sink = Sinks.many().multicast().directBestEffort();
        log.debug("Подписываемся на топик: {}", topic);

        Disposable redisSubscriber = redisTemplate.listenToChannel(topic)
                .map(ReactiveSubscription.Message::getMessage)
                .doOnNext(message -> {
                    log.debug("Получили сообщение из Redis для userId = {}: {}", userId, message);
                    Sinks.EmitResult result = sink.tryEmitNext(ServerSentEvent.builder()
                            .event(properties.getEventsProperties().getNotification().getEventName())
                            .data(message)
                            .comment(properties.getEventsProperties().getNotification().getComment())
                            .build());
                    if (result.isFailure()) {
                        log.warn("Не удалось отправить сообщение для userId = {}, причина: {}", userId, result);
                    }
                })
                .doOnError(error -> log.error("Ошибка в прослушивании Redis для topic = {}", topic, error))
                .retryWhen(
                        Retry.backoff(properties.getRedisRetryProperties().getMaxAttempts(),
                                        properties.getRedisRetryProperties().getInitialBackoff())
                                .maxBackoff(properties.getRedisRetryProperties().getMaxBackoff())
                                .jitter(properties.getRedisRetryProperties().getJitterFactor())
                                .doBeforeRetry(retrySignal ->
                                        log.warn("Переподключение к Redis для userId = {}, попытка = {}", userId,
                                                retrySignal.totalRetries() + 1))
                )
                .subscribe();
        return new Subscriber(sink, redisSubscriber);
    }

}
