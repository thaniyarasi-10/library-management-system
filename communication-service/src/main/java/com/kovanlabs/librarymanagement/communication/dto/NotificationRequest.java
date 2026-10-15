package com.kovanlabs.librarymanagement.communication.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.kovanlabs.librarymanagement.communication.enums.EmailTemplateType;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable Data Transfer Object carrying notification payload for email dispatch.
 * Contains recipient details, message content, template categorization, and optional
 * contextual data for overdue books or inactivity deactivations.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NotificationRequest(
        String recipient,
        String subject,
        String message,
        String userName,
        List<OverdueBookDto> overdueBooks,
        Double totalFine,
        EmailTemplateType templateType,
        Long inactiveDays,
        Long daysRemaining,
        LocalDate deactivationDate
) {
}
