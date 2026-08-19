package com.kovanlabs.librarymanagement.notification.service;

import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SmsNotificationService implements NotificationService {

    @Override
    public NotificationTypeEnum getType() {
        return NotificationTypeEnum.SMS;
    }

    @Override
    @Async
    public void send(NotificationRequest request) {
        log.info("Sending SMS notification to: {} | Content: [SMS]: {}", request.recipient(), request.message());
    }
}
