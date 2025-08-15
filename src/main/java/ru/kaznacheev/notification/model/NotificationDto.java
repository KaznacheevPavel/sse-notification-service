package ru.kaznacheev.notification.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class NotificationDto {

    private String type;
    private String payload;

}
