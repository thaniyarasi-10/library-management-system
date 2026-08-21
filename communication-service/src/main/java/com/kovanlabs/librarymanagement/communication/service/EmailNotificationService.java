package com.kovanlabs.librarymanagement.communication.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.MimeMessage;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@NoArgsConstructor

public class EmailNotificationService implements NotificationService {

    private static final String TEMPLATE_PATH = "templates/overdue_email.html";
    private static final double FINE_PER_DAY = 5.0;

    private JavaMailSender mailSender;

    @Autowired(required = false)
    public EmailNotificationService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public NotificationTypeEnum getType() {
        return NotificationTypeEnum.EMAIL;
    }

    @Override
    public void send(NotificationRequest request) {
        log.info("Sending Email notification to: {}", request.recipient());

        String body;
        if (request.overdueBooks() != null || request.userName() != null) {
            body = formatHtmlTemplate(request);
        } else {
            body = formatSimpleMessage(request.message());
        }

        if (mailSender != null) {
            try {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setTo(request.recipient());
                helper.setSubject(request.subject());
                helper.setText(body, true);
                mailSender.send(mimeMessage);
                log.info("HTML Email sent successfully via JavaMailSender to {}", request.recipient());
            } catch (Exception e) {
                log.error("Failed to send HTML Email via JavaMailSender to {}: {}", request.recipient(), e.getMessage(),
                        e);
            }
        } else {
            log.info("JavaMailSender not configured. Email notification fallback: {}", body);
        }
    }

    public String formatHtmlTemplate(NotificationRequest request) {
        String templateContent = loadTemplate(TEMPLATE_PATH);
        if (templateContent == null) {
            log.warn("HTML template file not found: {}. Falling back to default format.", TEMPLATE_PATH);
            return formatSimpleMessage(request.message());
        }

        String userName = request.userName() != null ? request.userName() : "Valued Member";
        List<OverdueBookDto> overdueBooks = request.overdueBooks();

        StringBuilder bookRowsHtml = new StringBuilder();
        double calculatedTotalFine = 0.0;

        if (overdueBooks != null && !overdueBooks.isEmpty()) {
            for (OverdueBookDto book : overdueBooks) {
                long daysOverdue = book.daysOverdue();
                if (daysOverdue <= 0 && book.dueDate() != null) {
                    daysOverdue = Math.max(0, ChronoUnit.DAYS.between(book.dueDate(), LocalDate.now()));
                }
                double fine = book.fine() > 0 ? book.fine() : (daysOverdue * FINE_PER_DAY);
                calculatedTotalFine += fine;

                bookRowsHtml.append("<tr>")
                        .append("<td>").append(escapeHtml(book.title())).append("</td>")
                        .append("<td>").append(escapeHtml(book.author() != null ? book.author() : "N/A"))
                        .append("</td>")
                        .append("<td>").append(book.dueDate() != null ? book.dueDate().toString() : "N/A")
                        .append("</td>")
                        .append("<td>").append(daysOverdue).append("</td>")
                        .append("<td>₹").append(formatCurrency(fine)).append("</td>")
                        .append("</tr>");
            }
        } else {
            bookRowsHtml.append("<tr><td colspan=\"5\">No overdue books listed</td></tr>");
        }

        double finalFine = (request.totalFine() != null && request.totalFine() > 0) ? request.totalFine()
                : calculatedTotalFine;

        return templateContent
                .replace("{{USER_NAME}}", escapeHtml(userName))
                .replace("{{BOOK_ROWS}}", bookRowsHtml.toString())
                .replace("{{TOTAL_FINE}}", "₹" + formatCurrency(finalFine));
    }

    private String formatSimpleMessage(String message) {
        if (message == null) {
            return "<html><body><p></p></body></html>";
        }
        if (message.startsWith("<!DOCTYPE html>") || message.startsWith("<html")) {
            return message;
        }
        return "<html><body><p>" + message + "</p></body></html>";
    }

    private String loadTemplate(String resourcePath) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to read template resource: {}", resourcePath, e);
            return null;
        }
    }

    private String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String formatCurrency(double amount) {
        if (amount == (long) amount) {
            return String.format("%d", (long) amount);
        }
        return String.format("%.2f", amount);
    }

}
