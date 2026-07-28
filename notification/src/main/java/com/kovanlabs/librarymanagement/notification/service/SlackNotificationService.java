package com.kovanlabs.librarymanagement.notification.service;

import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import org.springframework.stereotype.Service;

@Service
public class SlackNotificationService implements NotificationService {

    @Override
    public NotificationTypeEnum getType() {
        return NotificationTypeEnum.SMS;
    }

    @Override
    public void send(NotificationRequest request) {
        System.out.println("Sending SMS");
    }
}