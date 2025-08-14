package ru.kaznacheev.notification.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import ru.kaznacheev.notification.config.ApplicationProperties;
import ru.kaznacheev.notification.model.Subscriber;
import ru.kaznacheev.notification.service.NotificationService;
import ru.kaznacheev.notification.service.SubscriptionService;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final SubscriptionService subscriptionService;
    private final ApplicationProperties properties;
    private final Flux<ServerSentEvent<Object>> heartbeatStream;

    @Override
    public Flux<ServerSentEvent<Object>> createNotificationsStream(String userId) {
        Subscriber subscriber = subscriptionService.getOrCreateSubscriber(userId);
        subscriber.getSubscribersCount().incrementAndGet();

        return subscriber.getSink().asFlux()
                .mergeWith(heartbeatStream)
                .startWith(ServerSentEvent.builder()
                        .event(properties.getEventsProperties().getStart().getEventName())
                        .comment(properties.getEventsProperties().getStart().getComment())
                        .build())
                .doFinally(signalType -> {
                    if (subscriber.getSubscribersCount().decrementAndGet() == 0) {
                        subscriptionService.removeSubscriber(userId, subscriber);
                    }
                });
    }

}
