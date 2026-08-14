package com.kovanlabs.librarymanagement.notification.product;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SlackSender implements NotificationSender {

    @Override
    public void send(String recipient, String subject, String body) {
        log.info("Sending Slack notification to channel/user: {} | Title: {} | Message: {}", recipient, subject, body);
    }
}
