package com.kovanlabs.librarymanagement.communication.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.communication.enums.EmailTemplateType;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailNotificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailNotificationService(mailSender);
    }

    @Test
    @DisplayName("getType() should return EMAIL")
    void testGetType() {
        assertEquals(NotificationTypeEnum.EMAIL, service.getType());
    }

    @Test
    @DisplayName("send() with null or blank recipient should throw IllegalArgumentException")
    void testSend_NullOrBlankRecipient_ThrowsException() {
        NotificationRequest nullReq = null;
        assertThrows(IllegalArgumentException.class, () -> service.send(nullReq));

        NotificationRequest blankReq = new NotificationRequest(
                "   ", "Subject", "Message", null, null, null, EmailTemplateType.OVERDUE_FINE, null, null, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.send(blankReq));

        NotificationRequest nullRecipientReq = new NotificationRequest(
                null, "Subject", "Message", null, null, null, EmailTemplateType.OVERDUE_FINE, null, null, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.send(nullRecipientReq));

        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("send() with valid Overdue fine request should dispatch MimeMessage")
    void testSend_OverdueFine_Success() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        List<OverdueBookDto> books = List.of(
                new OverdueBookDto("Clean Code", "Robert Martin", LocalDate.now().minusDays(5), 5, 25.0)
        );
        NotificationRequest request = new NotificationRequest(
                "john@example.com",
                "Overdue Books Notice",
                "Please return overdue books",
                "John Doe",
                books,
                25.0,
                EmailTemplateType.OVERDUE_FINE,
                null,
                null,
                null
        );

        service.send(request);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        assertNotNull(captor.getValue());
    }

    @Test
    @DisplayName("send() with Inactivity Warning should dispatch MimeMessage")
    void testSend_InactivityWarning_Success() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRequest request = new NotificationRequest(
                "jane@example.com",
                "Notice: Account Inactivity Warning",
                null,
                "Jane Doe",
                null,
                null,
                EmailTemplateType.INACTIVITY_WARNING,
                23L,
                7L,
                LocalDate.now().plusDays(7)
        );

        service.send(request);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("send() with Account Deactivated notice should dispatch MimeMessage")
    void testSend_AccountDeactivated_Success() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationRequest request = new NotificationRequest(
                "deact@example.com",
                "Notice: Account Deactivated",
                null,
                "Deactivated User",
                null,
                null,
                EmailTemplateType.ACCOUNT_DEACTIVATED,
                31L,
                null,
                null
        );

        service.send(request);

        verify(mailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("send() without JavaMailSender should log warning gracefully")
    void testSend_WithoutMailSender() {
        EmailNotificationService serviceNoMail = new EmailNotificationService(null);
        NotificationRequest request = new NotificationRequest(
                "test@example.com",
                "Notice: Account Deactivated",
                null,
                "User",
                null,
                null,
                EmailTemplateType.ACCOUNT_DEACTIVATED,
                30L,
                null,
                null
        );

        assertDoesNotThrow(() -> serviceNoMail.send(request));
    }

    @Test
    @DisplayName("send() when mailSender throws exception should rethrow IllegalStateException")
    void testSend_MailSenderException_ThrowsIllegalStateException() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("SMTP connection failed")).when(mailSender).send(any(MimeMessage.class));

        NotificationRequest request = new NotificationRequest(
                "test@example.com",
                "Subject",
                null,
                "User",
                null,
                null,
                EmailTemplateType.ACCOUNT_DEACTIVATED,
                30L,
                null,
                null
        );

        assertThrows(IllegalStateException.class, () -> service.send(request));
    }

    @Test
    @DisplayName("send() with null subject should deduce default subjects for each template type")
    void testSend_NullSubject_SubjectDeductions() {
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        // 1. OVERDUE_FINE null subject
        NotificationRequest fineReq = new NotificationRequest(
                "user@example.com", null, null, "User",
                List.of(new OverdueBookDto("Book", "Author", LocalDate.now(), 1, 5.0)),
                5.0, EmailTemplateType.OVERDUE_FINE, null, null, null
        );
        service.send(fineReq);

        // 2. INACTIVITY_WARNING null subject
        NotificationRequest warnReq = new NotificationRequest(
                "user@example.com", null, null, "User",
                null, null, EmailTemplateType.INACTIVITY_WARNING, 24L, 6L, null
        );
        service.send(warnReq);

        // 3. ACCOUNT_DEACTIVATED null subject
        NotificationRequest deactReq = new NotificationRequest(
                "user@example.com", null, null, "User",
                null, null, EmailTemplateType.ACCOUNT_DEACTIVATED, 30L, null, null
        );
        service.send(deactReq);

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("formatEmailBody with null request should throw IllegalArgumentException")
    void testFormatEmailBody_NullRequest_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> service.formatEmailBody(null));
    }

    @Test
    @DisplayName("formatEmailBody with unresolvable template type should throw IllegalArgumentException")
    void testFormatEmailBody_UnresolvableTemplate_ThrowsException() {
        NotificationRequest unresolvable = new NotificationRequest(
                "user@example.com", "Sub", "Msg", "User", null, null, null, null, null, null
        );
        assertThrows(IllegalArgumentException.class, () -> service.formatEmailBody(unresolvable));
    }

    @Test
    @DisplayName("formatEmailBody for Overdue Fine should replace placeholders properly and escape HTML")
    void testFormatOverdueFineHtml() {
        List<OverdueBookDto> books = List.of(
                new OverdueBookDto("Book & <Special> \"Title\"", "Andy & Bob", LocalDate.now().minusDays(3), 3, 15.75),
                new OverdueBookDto("Refactoring", null, null, 0, 0.0)
        );
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Overdue Books Notice",
                "msg",
                "Alice & 'Bob'",
                books,
                15.75,
                EmailTemplateType.OVERDUE_FINE,
                null,
                null,
                null
        );

        String html = service.formatEmailBody(request);

        assertTrue(html.contains("Alice &amp; &#39;Bob&#39;"));
        assertTrue(html.contains("Book &amp; &lt;Special&gt; &quot;Title&quot;"));
        assertTrue(html.contains("Andy &amp; Bob"));
        assertTrue(html.contains("N/A"));
        assertTrue(html.contains("Total Pending Fine"));
        assertTrue(html.contains("15.75"));
    }

    @Test
    @DisplayName("formatEmailBody for Overdue Fine with calculated total fine")
    void testFormatOverdueFineHtml_CalculatedTotalFine() {
        List<OverdueBookDto> books = List.of(
                new OverdueBookDto("Book 1", "Author 1", LocalDate.now().minusDays(2), 2, 10.0),
                new OverdueBookDto("Book 2", "Author 2", LocalDate.now().minusDays(1), 0, 0.0)
        );
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Overdue Books Notice",
                "msg",
                "Bob",
                books,
                null,
                EmailTemplateType.OVERDUE_FINE,
                null,
                null,
                null
        );

        String html = service.formatEmailBody(request);
        assertTrue(html.contains("₹15"));
    }

    @Test
    @DisplayName("formatEmailBody for Overdue Fine with empty book list should handle gracefully")
    void testFormatOverdueFineHtml_EmptyBooks() {
        NotificationRequest request = new NotificationRequest(
                "user@example.com",
                "Overdue Books Notice",
                "msg",
                "",
                Collections.emptyList(),
                0.0,
                EmailTemplateType.OVERDUE_FINE,
                null,
                null,
                null
        );

        String html = service.formatEmailBody(request);

        assertTrue(html.contains("Valued Member"));
        assertTrue(html.contains("No overdue books listed"));
    }

    @Test
    @DisplayName("formatEmailBody for Inactivity Warning should render actual warning template")
    void testFormatInactivityWarningHtml() {
        LocalDate deactivationDate = LocalDate.now().plusDays(7);
        NotificationRequest requestWithDate = new NotificationRequest(
                "bob@example.com",
                "Warning",
                null,
                "Bob Jones",
                null,
                null,
                EmailTemplateType.INACTIVITY_WARNING,
                23L,
                7L,
                deactivationDate
        );

        String html = service.formatEmailBody(requestWithDate);
        assertTrue(html.contains("Bob Jones"));
        assertTrue(html.contains("23 days"));
        assertTrue(html.contains("in 7 days"));
        assertTrue(html.contains("Account Inactivity Warning"));

        // Without date and null user name
        NotificationRequest requestWithoutDate = new NotificationRequest(
                "bob@example.com",
                "Warning",
                null,
                null,
                null,
                null,
                EmailTemplateType.INACTIVITY_WARNING,
                25L,
                5L,
                null
        );
        String htmlNoDate = service.formatEmailBody(requestWithoutDate);
        assertTrue(htmlNoDate.contains("Valued Member"));
        assertTrue(htmlNoDate.contains("25 days"));
        assertTrue(htmlNoDate.contains("in 5 days"));
    }

    @Test
    @DisplayName("formatEmailBody for Account Deactivated should render actual deactivation template")
    void testFormatAccountDeactivatedHtml() {
        NotificationRequest request = new NotificationRequest(
                "charlie@example.com",
                "Deactivated",
                null,
                "Charlie Brown",
                null,
                null,
                EmailTemplateType.ACCOUNT_DEACTIVATED,
                30L,
                null,
                null
        );

        String html = service.formatEmailBody(request);

        assertTrue(html.contains("Charlie Brown"));
        assertTrue(html.contains("30 days"));
        assertTrue(html.contains("Account Deactivated"));
        assertTrue(html.contains("How to Reactivate Your Account"));

        // Blank username fallback
        NotificationRequest blankNameReq = new NotificationRequest(
                "charlie@example.com",
                "Deactivated",
                null,
                "   ",
                null,
                null,
                EmailTemplateType.ACCOUNT_DEACTIVATED,
                32L,
                null,
                null
        );
        String htmlBlank = service.formatEmailBody(blankNameReq);
        assertTrue(htmlBlank.contains("Valued Member"));
        assertTrue(htmlBlank.contains("32 days"));
    }

    @Test
    @DisplayName("determineTemplateType should deduce template when templateType is null")
    void testDetermineTemplateType_Deductions() {
        // Overdue books present -> OVERDUE_FINE
        NotificationRequest fineReq = new NotificationRequest(
                "u@ex.com", null, null, "User",
                List.of(new OverdueBookDto("Book", "A", LocalDate.now(), 1, 5.0)), 5.0,
                null, null, null, null
        );
        assertTrue(service.formatEmailBody(fineReq).contains("Book"));

        // inactiveDays and daysRemaining present -> INACTIVITY_WARNING
        NotificationRequest warnReq = new NotificationRequest(
                "u@ex.com", null, null, "User", null, null,
                null, 24L, 6L, null
        );
        assertTrue(service.formatEmailBody(warnReq).contains("24 days"));

        // only inactiveDays present -> ACCOUNT_DEACTIVATED
        NotificationRequest deactReq = new NotificationRequest(
                "u@ex.com", null, null, "User", null, null,
                null, 32L, null, null
        );
        assertTrue(service.formatEmailBody(deactReq).contains("Account Deactivated"));
    }
}
