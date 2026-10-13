package com.kovanlabs.librarymanagement.communication.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Service in communication-service responsible for formatting and sending
 * account inactivity warning emails.
 */
@Slf4j
@Service
public class InactivityNotificationService {

    private static final String TEMPLATE_PATH = "templates/account_inactivity_warning.html";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    private final JavaMailSender mailSender;

    public InactivityNotificationService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends an account inactivity warning email to the user.
     *
     * @param recipientEmail   User email address
     * @param userName         User display name
     * @param inactiveDays     Number of inactive days
     * @param daysRemaining    Days until deactivation
     * @param deactivationDate Target deactivation date
     * @return true if email was sent or logged successfully, false on invalid input or failure
     */
    public boolean sendInactivityWarning(String recipientEmail,
                                         String userName,
                                         long inactiveDays,
                                         long daysRemaining,
                                         LocalDate deactivationDate) {
        if (Objects.isNull(recipientEmail) || recipientEmail.isBlank()) {
            log.warn("Cannot send inactivity warning: recipient email is null or blank");
            return false;
        }

        String displayName = (Objects.nonNull(userName) && !userName.isBlank()) ? userName : "Valued Member";
        String formattedDate = Objects.nonNull(deactivationDate)
                ? deactivationDate.format(DATE_FORMATTER)
                : LocalDate.now().plusDays(daysRemaining).format(DATE_FORMATTER);

        String htmlBody = formatWarningEmailHtml(displayName, inactiveDays, daysRemaining, formattedDate);
        String subject = "Notice: Account Inactivity Warning - Library Management System";

        if (Objects.nonNull(mailSender)) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setTo(recipientEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);
                mailSender.send(mimeMessage);
                log.info("Inactivity warning HTML email sent successfully via JavaMailSender to {}", recipientEmail);
                return true;
            } catch (Exception e) {
                log.error("Failed to send inactivity warning email via JavaMailSender to {}: {}",
                        recipientEmail, e.getMessage(), e);
                return false;
            }
        } else {
            log.info("JavaMailSender not configured. Inactivity warning email logged for {}: {}",
                    recipientEmail, htmlBody);
            return true;
        }
    }

    public String formatWarningEmailHtml(String userName, long inactiveDays, long daysRemaining, String deactivationDate) {
        String template = loadTemplate(TEMPLATE_PATH);
        if (Objects.isNull(template)) {
            return "<!DOCTYPE html><html><body><p>Dear " + escapeHtml(userName)
                    + ", your account has been inactive for " + inactiveDays
                    + " days and is scheduled for deactivation on " + deactivationDate
                    + ". Please log in to keep your account active.</p></body></html>";
        }

        return template
                .replace("{{USER_NAME}}", escapeHtml(userName))
                .replace("{{INACTIVE_DAYS}}", String.valueOf(inactiveDays))
                .replace("{{DAYS_REMAINING}}", String.valueOf(daysRemaining))
                .replace("{{DEACTIVATION_DATE}}", escapeHtml(deactivationDate));
    }

    private String loadTemplate(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to load inactivity email template from {}: {}", resourcePath, e.getMessage());
            return null;
        }
    }

    private String escapeHtml(String text) {
        return Objects.isNull(text)
                ? ""
                : text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
