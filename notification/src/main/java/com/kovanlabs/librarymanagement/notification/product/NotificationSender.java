package com.kovanlabs.librarymanagement.notification.product;

public interface NotificationSender {
    void send(String recipient, String subject, String body);
}
