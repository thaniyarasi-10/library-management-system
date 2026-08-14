package com.kovanlabs.librarymanagement.notification.product;

import org.springframework.stereotype.Component;

@Component
public class EmailFormatter implements NotificationFormatter {

    @Override
    public String format(String message) {
        return "<html><body><p>" + message + "</p></body></html>";
    }
}
