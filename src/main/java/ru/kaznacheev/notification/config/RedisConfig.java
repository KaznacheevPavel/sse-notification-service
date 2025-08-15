package ru.kaznacheev.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import ru.kaznacheev.notification.model.NotificationDto;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, NotificationDto> notificationRedisTemplate(ReactiveRedisConnectionFactory connectionFactory) {
        Jackson2JsonRedisSerializer<NotificationDto> serializer = new Jackson2JsonRedisSerializer<>(NotificationDto.class);
        RedisSerializationContext<String, NotificationDto> serializationContext =
                RedisSerializationContext.<String, NotificationDto>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .hashValue(serializer)
                        .hashKey(new StringRedisSerializer())
                        .build();

        return new ReactiveRedisTemplate<>(connectionFactory, serializationContext);
    }

}
