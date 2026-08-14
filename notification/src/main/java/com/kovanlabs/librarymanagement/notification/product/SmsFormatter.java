package com.kovanlabs.librarymanagement.notification.product;

import org.springframework.stereotype.Component;

@Component
public class SmsFormatter implements NotificationFormatter {

    @Override
    public String format(String message) {
        return "[SMS]: " + message;
    }
}
