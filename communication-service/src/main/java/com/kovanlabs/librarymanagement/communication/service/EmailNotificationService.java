package com.kovanlabs.librarymanagement.communication.service;

import com.kovanlabs.librarymanagement.communication.dto.NotificationRequest;
import com.kovanlabs.librarymanagement.communication.dto.OverdueBookDto;
import com.kovanlabs.librarymanagement.communication.enums.NotificationTypeEnum;
import jakarta.mail.internet.MimeMessage;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

        String body = (Objects.nonNull(request.overdueBooks()) || Objects.nonNull(request.userName()))
                ? formatHtmlTemplate(request)
                : formatSimpleMessage(request.message());

        if (Objects.nonNull(mailSender)) {
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
        if (Objects.isNull(templateContent)) {
            log.warn("HTML template file not found: {}. Falling back to default format.", TEMPLATE_PATH);
            return formatSimpleMessage(request.message());
        }

        String userName = Objects.nonNull(request.userName()) ? request.userName() : "Valued Member";
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

        double finalFine = (Objects.nonNull(request.totalFine()) && request.totalFine() > 0) ? request.totalFine()
                : calculatedTotalFine;

        return templateContent
                .replace("{{USER_NAME}}", escapeHtml(userName))
                .replace("{{BOOK_ROWS}}", bookRowsHtml)
                .replace("{{TOTAL_FINE}}", "₹" + formatCurrency(finalFine));
    }

    private String formatSimpleMessage(String message) {
        if (Objects.isNull(message)) {
            return "<html><body><p></p></body></html>";
        }
        return (message.startsWith("<!DOCTYPE html>") || message.startsWith("<html"))
                ? message
                : "<html><body><p>" + message + "</p></body></html>";
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
        return Objects.isNull(text)
                ? ""
                : text.replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#39;");
    }

    private String formatCurrency(double amount) {
        return (amount == (long) amount)
                ? String.format("%d", (long) amount)
                : String.format("%.2f", amount);
    }

}
