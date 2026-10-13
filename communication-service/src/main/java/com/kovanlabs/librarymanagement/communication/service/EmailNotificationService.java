package com.kovanlabs.librarymanagement.communication.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.communication.enums.EmailTemplateType;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Unified email notification service implementing {@link NotificationService}.
 * <p>
 * Renders HTML email templates and dispatches emails via {@link JavaMailSender} for:
 * <ul>
 *     <li><b>Overdue Fine Notice:</b> Renders {@code templates/overdue_email.html} with overdue book itemization and fines.</li>
 *     <li><b>Inactivity Warning Notice:</b> Renders {@code templates/account_inactivity_warning.html} informing users of pending deactivation.</li>
 *     <li><b>Account Deactivated Notice:</b> Renders {@code templates/account_deactivated.html} informing users of deactivation and reactivation instructions.</li>
 * </ul>
 * <p>
 * Throws explicit exceptions when templates cannot be loaded, inputs are invalid, or email delivery fails.
 */
@Slf4j
@Service
public class EmailNotificationService implements NotificationService {

    private static final String OVERDUE_FINE_TEMPLATE = "templates/overdue_email.html";
    private static final String INACTIVITY_WARNING_TEMPLATE = "templates/account_inactivity_warning.html";
    private static final String ACCOUNT_DEACTIVATED_TEMPLATE = "templates/account_deactivated.html";

    private static final double FINE_PER_DAY = 5.0;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    private final JavaMailSender mailSender;

    /**
     * Constructs the {@code EmailNotificationService} with an optional {@link JavaMailSender}.
     *
     * @param mailSender The Spring JavaMailSender bean
     */
    public EmailNotificationService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Identifies the notification transport type supported by this service.
     *
     * @return {@link NotificationTypeEnum#EMAIL}
     */
    @Override
    public NotificationTypeEnum getType() {
        return NotificationTypeEnum.EMAIL;
    }

    /**
     * Asynchronously formats and delivers an email notification based on the provided request.
     *
     * @param request The {@link NotificationRequest} containing recipient, subject, and template payload
     * @throws IllegalArgumentException if the request or recipient is null or blank, or if template type is unresolved
     * @throws IllegalStateException    if email template loading fails or email sending fails
     */
    @Override
    @Async
    public void send(NotificationRequest request) {
        if (Objects.isNull(request) || Objects.isNull(request.recipient()) || request.recipient().isBlank()) {
            throw new IllegalArgumentException("Notification request and recipient must not be null or blank");
        }

        log.info("Sending Email notification to: {} | Type: {}", request.recipient(), request.templateType());

        String body = formatEmailBody(request);
        String subject = determineSubject(request);

        if (Objects.nonNull(mailSender)) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());
                helper.setTo(request.recipient());
                helper.setSubject(subject);
                helper.setText(body, true);
                mailSender.send(mimeMessage);
                log.info("HTML Email sent successfully via JavaMailSender to {}", request.recipient());
            } catch (Exception e) {
                log.error("Failed to send HTML Email via JavaMailSender to {}: {}", request.recipient(), e.getMessage(), e);
                throw new IllegalStateException("Failed to send email notification to: " + request.recipient(), e);
            }
        } else {
            log.warn("JavaMailSender is not configured. Email could not be sent to: {}", request.recipient());
        }
    }

    /**
     * Resolves the template category and generates the corresponding HTML email body using actual templates.
     *
     * @param request The notification request containing payload data
     * @return Formatted HTML string ready for email transmission
     * @throws IllegalArgumentException if the template type cannot be determined
     * @throws IllegalStateException    if the template file cannot be loaded
     */
    public String formatEmailBody(NotificationRequest request) {
        if (Objects.isNull(request)) {
            throw new IllegalArgumentException("Notification request must not be null");
        }

        EmailTemplateType templateType = determineTemplateType(request);

        return switch (templateType) {
            case OVERDUE_FINE -> formatOverdueFineHtml(request);
            case INACTIVITY_WARNING -> formatInactivityWarningHtml(request);
            case ACCOUNT_DEACTIVATED -> formatAccountDeactivatedHtml(request);
        };
    }

    /**
     * Determines the appropriate {@link EmailTemplateType} based on explicit request type
     * or context inspection (presence of overdue books or inactivity metadata).
     *
     * @param request The notification request to evaluate
     * @return The resolved {@link EmailTemplateType}
     * @throws IllegalArgumentException if no matching template type can be identified
     */
    private EmailTemplateType determineTemplateType(NotificationRequest request) {
        if (Objects.nonNull(request.templateType())) {
            return request.templateType();
        }
        if (Objects.nonNull(request.overdueBooks()) && !request.overdueBooks().isEmpty()) {
            return EmailTemplateType.OVERDUE_FINE;
        }
        if (Objects.nonNull(request.inactiveDays())) {
            if (Objects.nonNull(request.daysRemaining()) || Objects.nonNull(request.deactivationDate())) {
                return EmailTemplateType.INACTIVITY_WARNING;
            }
            return EmailTemplateType.ACCOUNT_DEACTIVATED;
        }
        throw new IllegalArgumentException("Unable to determine email template type for notification request to: " + request.recipient());
    }

    /**
     * Determines the email subject line, using explicit request subject if present,
     * or deriving a standard descriptive subject according to the template type.
     *
     * @param request The notification request
     * @return Suitable email subject line
     */
    private String determineSubject(NotificationRequest request) {
        if (Objects.nonNull(request.subject()) && !request.subject().isBlank()) {
            return request.subject();
        }
        EmailTemplateType templateType = determineTemplateType(request);
        return switch (templateType) {
            case OVERDUE_FINE -> "Notice: Overdue Library Books";
            case INACTIVITY_WARNING -> "Notice: Account Inactivity Warning - Library Management System";
            case ACCOUNT_DEACTIVATED -> "Notice: Account Deactivated - Library Management System";
        };
    }

    /**
     * Formats the overdue book reminder email using actual {@code templates/overdue_email.html}.
     * Populates book table rows with title, author, due date, days overdue, and calculated fine.
     *
     * @param request The notification request containing overdue books and optional total fine
     * @return Populated HTML string
     * @throws IllegalStateException if the template cannot be loaded
     */
    public String formatOverdueFineHtml(NotificationRequest request) {
        String templateContent = loadTemplate(OVERDUE_FINE_TEMPLATE);

        String userName = (Objects.nonNull(request.userName()) && !request.userName().isBlank())
                ? request.userName()
                : "Valued Member";
        List<OverdueBookDto> overdueBooks = request.overdueBooks();

        String bookRowsHtml = (Objects.nonNull(overdueBooks) && !overdueBooks.isEmpty())
                ? overdueBooks.stream()
                        .map(book -> {
                            long daysOverdue = (book.daysOverdue() <= 0 && Objects.nonNull(book.dueDate()))
                                    ? Math.max(0, ChronoUnit.DAYS.between(book.dueDate(), LocalDate.now()))
                                    : book.daysOverdue();
                            double fine = book.fine() > 0 ? book.fine() : (daysOverdue * FINE_PER_DAY);
                            return "<tr>"
                                    + "<td>" + escapeHtml(book.title()) + "</td>"
                                    + "<td>" + escapeHtml(Objects.nonNull(book.author()) ? book.author() : "N/A") + "</td>"
                                    + "<td>" + (Objects.nonNull(book.dueDate()) ? book.dueDate().toString() : "N/A") + "</td>"
                                    + "<td>" + daysOverdue + "</td>"
                                    + "<td>₹" + formatCurrency(fine) + "</td>"
                                    + "</tr>";
                        })
                        .collect(Collectors.joining())
                : "<tr><td colspan=\"5\">No overdue books listed</td></tr>";

        double calculatedTotalFine = (Objects.nonNull(overdueBooks) && !overdueBooks.isEmpty())
                ? overdueBooks.stream()
                        .mapToDouble(book -> {
                            long daysOverdue = (book.daysOverdue() <= 0 && Objects.nonNull(book.dueDate()))
                                    ? Math.max(0, ChronoUnit.DAYS.between(book.dueDate(), LocalDate.now()))
                                    : book.daysOverdue();
                            return book.fine() > 0 ? book.fine() : (daysOverdue * FINE_PER_DAY);
                        })
                        .sum()
                : 0.0;

        double finalFine = (Objects.nonNull(request.totalFine()) && request.totalFine() > 0)
                ? request.totalFine()
                : calculatedTotalFine;

        return templateContent
                .replace("{{USER_NAME}}", escapeHtml(userName))
                .replace("{{BOOK_ROWS}}", bookRowsHtml)
                .replace("{{TOTAL_FINE}}", "₹" + formatCurrency(finalFine));
    }

    /**
     * Formats the account inactivity warning email using actual {@code templates/account_inactivity_warning.html}.
     * Informs the user of inactive duration, scheduled deactivation date, and steps to keep account active.
     *
     * @param request The notification request containing user name, inactive days, and deactivation date
     * @return Populated HTML string
     * @throws IllegalStateException if the template cannot be loaded
     */
    public String formatInactivityWarningHtml(NotificationRequest request) {
        String templateContent = loadTemplate(INACTIVITY_WARNING_TEMPLATE);
        String userName = (Objects.nonNull(request.userName()) && !request.userName().isBlank())
                ? request.userName()
                : "Valued Member";
        long inactiveDays = Objects.nonNull(request.inactiveDays()) ? request.inactiveDays() : 0L;
        long daysRemaining = Objects.nonNull(request.daysRemaining()) ? request.daysRemaining() : 0L;
        String formattedDate = Objects.nonNull(request.deactivationDate())
                ? request.deactivationDate().format(DATE_FORMATTER)
                : LocalDate.now().plusDays(daysRemaining).format(DATE_FORMATTER);

        return templateContent
                .replace("{{USER_NAME}}", escapeHtml(userName))
                .replace("{{INACTIVE_DAYS}}", String.valueOf(inactiveDays))
                .replace("{{DAYS_REMAINING}}", String.valueOf(daysRemaining))
                .replace("{{DEACTIVATION_DATE}}", escapeHtml(formattedDate));
    }

    /**
     * Formats the account deactivated notice using actual {@code templates/account_deactivated.html}.
     * Informs the user that the account has been deactivated and outlines reactivation request steps.
     *
     * @param request The notification request containing user name and inactive days
     * @return Populated HTML string
     * @throws IllegalStateException if the template cannot be loaded
     */
    public String formatAccountDeactivatedHtml(NotificationRequest request) {
        String templateContent = loadTemplate(ACCOUNT_DEACTIVATED_TEMPLATE);
        String userName = (Objects.nonNull(request.userName()) && !request.userName().isBlank())
                ? request.userName()
                : "Valued Member";
        long inactiveDays = Objects.nonNull(request.inactiveDays()) ? request.inactiveDays() : 0L;

        return templateContent
                .replace("{{USER_NAME}}", escapeHtml(userName))
                .replace("{{INACTIVE_DAYS}}", String.valueOf(inactiveDays));
    }

    /**
     * Loads an HTML template file from the classpath resources.
     *
     * @param resourcePath Classpath path to the template
     * @return File content as UTF-8 string
     * @throws IllegalStateException if template loading fails
     */
    private String loadTemplate(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to read template resource: {}", resourcePath, e);
            throw new IllegalStateException("Failed to load email template: " + resourcePath, e);
        }
    }

    /**
     * Escapes critical HTML control characters to prevent HTML/script injection in email templates.
     *
     * @param text Raw text input
     * @return HTML-escaped string
     */
    private String escapeHtml(String text) {
        return Objects.isNull(text)
                ? ""
                : text.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#39;");
    }

    /**
     * Formats fine amounts to string without decimal places if whole number, or with 2 decimals.
     *
     * @param amount Currency amount
     * @return Formatted string representation
     */
    private String formatCurrency(double amount) {
        return (amount == (long) amount)
                ? String.format("%d", (long) amount)
                : String.format("%.2f", amount);
    }
}
