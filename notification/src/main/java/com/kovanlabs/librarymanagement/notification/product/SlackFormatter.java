package com.kovanlabs.librarymanagement.notification.product;

import org.springframework.stereotype.Component;

@Component
public class SlackFormatter implements NotificationFormatter {

    @Override
    public String format(String message) {
        return "*[SLACK ALERT]*\n" + message;
    }
}
