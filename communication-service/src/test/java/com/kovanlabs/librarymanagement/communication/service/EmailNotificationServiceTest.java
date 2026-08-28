package com.kovanlabs.librarymanagement.communication.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private EmailNotificationService emailNotificationService;

    @BeforeEach
    void setUp() {
        emailNotificationService = new EmailNotificationService(mailSender);
    }

    @Test
    @DisplayName("getType should return EMAIL")
    void getType_shouldReturnEmail() {
        assertEquals(NotificationTypeEnum.EMAIL, emailNotificationService.getType());
    }

    @Test
    @DisplayName("send email with JavaMailSender successfully")
    void send_withJavaMailSender_shouldSendEmailSuccessfully() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Overdue Book Notice",
                "Your book is overdue",
                "John Doe",
                List.of(new OverdueBookDto("The Great Gatsby", "F. Scott Fitzgerald", LocalDate.now().minusDays(5), 5, 25.0)),
                25.0
        );

        emailNotificationService.send(request);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("format HTML template with overdue books and fine")
    void formatHtmlTemplate_withOverdueBooksAndNullFine_shouldCalculateFine() {
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Subject",
                "Message",
                "Jane & Doe <Test>",
                List.of(
                        new OverdueBookDto("Book 1", "Author 1", LocalDate.now().minusDays(3), 0, 0.0),
                        new OverdueBookDto("Book 2", null, null, 2, 10.5)
                ),
                0.0
        );

        String html = emailNotificationService.formatHtmlTemplate(request);

        assertNotNull(html);
        assertTrue(html.contains("Jane &amp; Doe &lt;Test&gt;"));
        assertTrue(html.contains("Book 1"));
        assertTrue(html.contains("Book 2"));
    }

    @Test
    @DisplayName("JavaMailSender throws exception during send, method handles exception gracefully without throwing")
    void send_whenMailSenderThrowsException_shouldHandleException() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Mail server unavailable")).when(mailSender).send(any(MimeMessage.class));

        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Subject",
                "Simple message"
        );

        assertDoesNotThrow(() -> emailNotificationService.send(request));
    }

    @Test
    @DisplayName("send email when JavaMailSender is null (fallback mode)")
    void send_withoutJavaMailSender_shouldFallbackGracefully() {
        EmailNotificationService serviceNoMailSender = new EmailNotificationService(null);

        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Subject",
                "Simple message"
        );

        assertDoesNotThrow(() -> serviceNoMailSender.send(request));
    }

    @Test
    @DisplayName("format HTML template with empty overdue books list")
    void formatHtmlTemplate_withEmptyOverdueBooks_shouldRenderNoBooksRow() {
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Subject",
                "Message",
                "User",
                Collections.emptyList(),
                10.0
        );

        String html = emailNotificationService.formatHtmlTemplate(request);

        assertNotNull(html);
        assertTrue(html.contains("No overdue books listed"));
    }

    @Test
    @DisplayName("format HTML template with null message")
    void formatHtmlTemplate_withNullMessage_shouldReturnEmptyParagraphHtml() {
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Subject",
                null
        );

        emailNotificationService.send(request);
        verify(mailSender, times(1)).createMimeMessage();
    }

    @Test
    @DisplayName("send simple text message format when overdueBooks and userName are null")
    void send_withSimpleTextAndNoTemplateFields() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRequest request = new NotificationRequest("user@example.com", "Subject", "Hello world");
        assertDoesNotThrow(() -> emailNotificationService.send(request));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("send simple html formatted message starting with html tag")
    void send_withHtmlFormattedMessage() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRequest request = new NotificationRequest("user@example.com", "Subject", "<html><body>Custom</body></html>");
        assertDoesNotThrow(() -> emailNotificationService.send(request));
        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }
}
