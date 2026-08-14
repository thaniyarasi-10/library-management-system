package com.kovanlabs.librarymanagement.notification.factory;

import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationFactoryProvider {

    private final Map<NotificationTypeEnum, NotificationAbstractFactory> factoryMap;

    public NotificationFactoryProvider(List<NotificationAbstractFactory> factories) {
        this.factoryMap = factories.stream()
                .collect(Collectors.toMap(
                        NotificationAbstractFactory::getType,
                        Function.identity()
                ));
    }

    public NotificationAbstractFactory getFactory(NotificationTypeEnum type) {
        NotificationAbstractFactory factory = factoryMap.get(type);
        if (factory == null) {
            throw new IllegalArgumentException("Notification type not supported: " + type);
        }
        return factory;
    }
}
