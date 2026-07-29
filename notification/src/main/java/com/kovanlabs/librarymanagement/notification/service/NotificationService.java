package com.kovanlabs.librarymanagement.notification.service;

import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import org.springframework.scheduling.annotation.Async;

public interface NotificationService {
    NotificationTypeEnum getType();

    @Async
    void send(NotificationRequest request);
}

