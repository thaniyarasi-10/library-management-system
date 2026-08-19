package com.kovanlabs.librarymanagement.notification.factory;

import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.service.NotificationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NotificationFactory {

    private final Map<NotificationTypeEnum, NotificationService> notificationMap;

    public NotificationFactory(List<NotificationService> notificationServices) {
        this.notificationMap = notificationServices.stream()
                .collect(Collectors.toMap(
                        NotificationService::getType,
                        Function.identity()
                ));
    }

    public NotificationService get(NotificationTypeEnum type) {
        NotificationService service = notificationMap.get(type);
        if (service == null) {
            throw new IllegalArgumentException("Notification type not supported: " + type);
        }
        return service;
    }
}
