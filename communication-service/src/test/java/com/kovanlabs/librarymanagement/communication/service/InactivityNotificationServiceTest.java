package com.kovanlabs.librarymanagement.communication.service;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InactivityNotificationServiceTest {

    private JavaMailSender mailSender;
    private InactivityNotificationService service;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        service = new InactivityNotificationService(mailSender);
    }

    @Test
    @DisplayName("sendInactivityWarning with null or blank recipient should return false")
    void sendInactivityWarning_nullRecipient_shouldReturnFalse() {
        assertFalse(service.sendInactivityWarning(null, "Alice", 23, 7, LocalDate.now().plusDays(7)));
        assertFalse(service.sendInactivityWarning("", "Alice", 23, 7, LocalDate.now().plusDays(7)));
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("sendInactivityWarning with mailSender configured should send HTML email")
    void sendInactivityWarning_withMailSender_shouldSend() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        boolean result = service.sendInactivityWarning(
                "alice@example.com",
                "Alice Smith",
                23,
                7,
                LocalDate.of(2026, 11, 1)
        );

        assertTrue(result);
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("sendInactivityWarning when mailSender is null should fallback and log without error")
    void sendInactivityWarning_nullMailSender_shouldLogFallback() {
        InactivityNotificationService serviceWithoutSender = new InactivityNotificationService(null);

        boolean result = serviceWithoutSender.sendInactivityWarning(
                "bob@example.com",
                "Bob",
                25,
                5,
                LocalDate.of(2026, 11, 3)
        );

        assertTrue(result);
    }

    @Test
    @DisplayName("sendInactivityWarning when mailSender throws exception should return false")
    void sendInactivityWarning_mailException_shouldReturnFalse() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP connect failed")).when(mailSender).send(mimeMessage);

        boolean result = service.sendInactivityWarning(
                "charlie@example.com",
                "Charlie",
                23,
                7,
                LocalDate.now().plusDays(7)
        );

        assertFalse(result);
    }

    @Test
    @DisplayName("formatWarningEmailHtml should substitute template variables properly")
    void formatWarningEmailHtml_shouldReplacePlaceholders() {
        String html = service.formatWarningEmailHtml("Diana Prince", 23, 7, "Nov 01, 2026");

        assertNotNull(html);
        assertTrue(html.contains("Diana Prince"));
        assertTrue(html.contains("23"));
        assertTrue(html.contains("7"));
        assertTrue(html.contains("Nov 01, 2026"));
    }
}
