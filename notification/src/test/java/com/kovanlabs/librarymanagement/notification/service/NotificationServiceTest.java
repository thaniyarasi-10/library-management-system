package com.kovanlabs.librarymanagement.notification.service;

import com.kovanlabs.librarymanagement.notification.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.notification.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.notification.enums.NotificationTypeEnum;
import com.kovanlabs.librarymanagement.notification.factory.NotificationFactory;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private JavaMailSender javaMailSender;
    private EmailNotificationService emailNotificationService;
    private SmsNotificationService smsNotificationService;
    private NotificationFactory notificationFactory;

    @BeforeEach
    void setUp() {
        javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = mock(MimeMessage.class);
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailNotificationService = new EmailNotificationService(javaMailSender);
        smsNotificationService = new SmsNotificationService();

        notificationFactory = new NotificationFactory(List.of(emailNotificationService, smsNotificationService));
    }

    @Test
    @DisplayName("NotificationFactory should return appropriate NotificationService for EMAIL and SMS")
    void testNotificationFactoryResolution() {
        NotificationService emailService = notificationFactory.get(NotificationTypeEnum.EMAIL);
        assertNotNull(emailService);
        assertEquals(NotificationTypeEnum.EMAIL, emailService.getType());

        NotificationService smsService = notificationFactory.get(NotificationTypeEnum.SMS);
        assertNotNull(smsService);
        assertEquals(NotificationTypeEnum.SMS, smsService.getType());
    }

    @Test
    @DisplayName("EmailNotificationService should format HTML template with fine of ₹5/day/book")
    void testFormatHtmlTemplateAndFineCalculation() {
        OverdueBookDto book1 = new OverdueBookDto("Clean Code", "Robert C. Martin", LocalDate.now().minusDays(3), 3, 0);
        OverdueBookDto book2 = new OverdueBookDto("Design Patterns", "Erich Gamma", LocalDate.now().minusDays(5), 5, 0);

        NotificationRequest request = new NotificationRequest(
                "john.doe@example.com",
                "Overdue Books Notice",
                "Please return your overdue books",
                "John Doe",
                List.of(book1, book2),
                null
        );

        String htmlContent = emailNotificationService.formatHtmlTemplate(request);

        assertNotNull(htmlContent);
        assertTrue(htmlContent.contains("John Doe"));
        assertTrue(htmlContent.contains("Clean Code"));
        assertTrue(htmlContent.contains("Design Patterns"));
        assertTrue(htmlContent.contains("₹40"));
        assertTrue(htmlContent.contains("₹15"));
        assertTrue(htmlContent.contains("₹25"));
    }

    @Test
    @DisplayName("EmailNotificationService should send HTML email via JavaMailSender")
    void testSendEmailNotification() {
        OverdueBookDto book = new OverdueBookDto("Spring in Action", "Craig Walls", LocalDate.now().minusDays(2), 2, 10.0);
        NotificationRequest request = new NotificationRequest(
                "jane.smith@example.com",
                "Library Overdue Alert",
                "Notice",
                "Jane Smith",
                List.of(book),
                10.0
        );

        assertDoesNotThrow(() -> emailNotificationService.send(request));

        verify(javaMailSender, times(1)).createMimeMessage();
        verify(javaMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SmsNotificationService should execute send without error")
    void testSendSmsNotification() {
        NotificationRequest request = new NotificationRequest("9876543210", "Alert", "Your book is due tomorrow");
        assertDoesNotThrow(() -> smsNotificationService.send(request));
    }
}
