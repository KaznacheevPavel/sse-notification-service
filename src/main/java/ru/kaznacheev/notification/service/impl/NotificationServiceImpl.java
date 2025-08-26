package ru.kaznacheev.notification.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.kaznacheev.notification.config.ApplicationProperties;
import ru.kaznacheev.notification.model.Subscriber;
import ru.kaznacheev.notification.service.NotificationService;
import ru.kaznacheev.notification.service.SubscriptionService;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SubscriptionService subscriptionService;
    private final ApplicationProperties properties;
    private final Flux<ServerSentEvent<Object>> heartbeatStream;

    @Override
    public Flux<ServerSentEvent<Object>> createNotificationsStream(String userId) {
        log.info("Подключение клиента: userId = {}", userId);
        Subscriber subscriber = subscriptionService.getOrCreateSubscriber(userId);
        int subscriberCount = subscriber.getSubscribersCount().incrementAndGet();
        log.debug("Количество подписчиков для userId = {} увеличилось до {}", userId, subscriberCount);

        return subscriber.getSink().asFlux()
                .mergeWith(heartbeatStream)
                .startWith(ServerSentEvent.builder()
                        .event(properties.getEventsProperties().getStart().getEventName())
                        .comment(properties.getEventsProperties().getStart().getComment())
                        .build())
                .doFinally(signalType -> {
                    int subscriberLeft = subscriber.getSubscribersCount().decrementAndGet();
                    log.info("Отключение клиента: userId = {}, осталось подписчиков - {}", userId, subscriberLeft);
                    if (subscriberLeft == 0) {
                        subscriptionService.removeSubscriber(userId, subscriber);
                    }
                });
    }

}
