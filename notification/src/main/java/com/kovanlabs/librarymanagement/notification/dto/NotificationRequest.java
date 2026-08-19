package com.kovanlabs.librarymanagement.notification.dto;

import java.util.List;

public record NotificationRequest(
        String recipient,
        String subject,
        String message,
        String userName,
        List<OverdueBookDto> overdueBooks,
        Double totalFine) {

    public NotificationRequest(String recipient, String subject, String message) {
        this(recipient, subject, message, null, null, null);
    }
}
