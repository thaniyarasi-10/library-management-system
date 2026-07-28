package com.kovanlabs.librarymanagement.notification.dto;

public record NotificationRequest(
        String recipient,
        String subject,
        String message) {
}
