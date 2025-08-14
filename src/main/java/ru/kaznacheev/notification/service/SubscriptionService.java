package ru.kaznacheev.notification.service;

import ru.kaznacheev.notification.model.Subscriber;

public interface SubscriptionService {

    Subscriber getOrCreateSubscriber(String userId);

    void removeSubscriber(String userId, Subscriber subscriber);

}
