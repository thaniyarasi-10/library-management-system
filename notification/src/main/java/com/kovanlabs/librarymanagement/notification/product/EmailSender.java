package com.kovanlabs.librarymanagement.notification.product;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class EmailSender implements NotificationSender {

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("Sending Email notification to: {} | Subject: {} | Body: {}", recipient, subject, body);
    }
}
