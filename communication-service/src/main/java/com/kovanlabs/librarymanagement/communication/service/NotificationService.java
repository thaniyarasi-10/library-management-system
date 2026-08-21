package com.kovanlabs.librarymanagement.communication.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import org.springframework.scheduling.annotation.Async;

public interface NotificationService {
    NotificationTypeEnum getType();

    @Async
    void send(NotificationRequest request);
}


