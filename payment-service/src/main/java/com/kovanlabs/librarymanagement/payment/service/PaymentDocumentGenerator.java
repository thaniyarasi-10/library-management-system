package com.kovanlabs.librarymanagement.payment.service;

import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.database.enums.TransactionType;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Component
public class PaymentDocumentGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private volatile String templateCache;

    /**
     * Generates a deterministic, unique document number for a payment.
     * Reuses an already assigned document number if present.
     */
    public String generateDocumentNumber(Payment payment) {
        if (payment.getDocumentNumber() != null && !payment.getDocumentNumber().isBlank()) {
            return payment.getDocumentNumber();
        }
        String prefix;
        if (payment.getTransactionType() == TransactionType.REFUND) {
            prefix = "REF";
        } else if (payment.getStatus() == PaymentStatus.SUCCESS) {
            prefix = "REC";
        } else {
            prefix = "STMT";
        }

        String yearMonth = payment.getCreatedAt() != null
                ? payment.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyyMM"))
                : LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));

        String rawUuid = Objects.requireNonNullElse(payment.getUuid(), UUID.randomUUID().toString())
                .replace("-", "");
        String suffix = rawUuid.substring(0, Math.min(8, rawUuid.length())).toUpperCase();

        return String.format("%s-%s-%s", prefix, yearMonth, suffix);
    }

    /**
     * Compiles the payment and user data into an official PDF document using the common HTML template.
     *
     * @param payment        The payment record
     * @param user           The paying/affected user
     * @param refunds        Associated refunds (if applicable)
     * @param documentNumber Document number assigned to this document
     * @return Rendered PDF byte array
     */
    public byte[] generatePdf(Payment payment, User user, List<Payment> refunds, String documentNumber) throws Exception {
        log.info("Generating PDF document {} for payment uuid: {}, status: {}", documentNumber, payment.getUuid(), payment.getStatus());
        String htmlContent = buildHtmlFromTemplate(payment, user, refunds, documentNumber);
        return renderHtmlToPdf(htmlContent);
    }

    private byte[] renderHtmlToPdf(String htmlContent) throws Exception {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, "/");
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }

    private String loadTemplate() {
        if (templateCache != null) {
            return templateCache;
        }
        try (InputStream is = getClass().getResourceAsStream("/templates/payment-document-template.html")) {
            if (is == null) {
                throw new IllegalStateException("Payment document HTML template not found at /templates/payment-document-template.html");
            }
            templateCache = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return templateCache;
        } catch (Exception e) {
            log.error("Failed to load payment document HTML template: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to load payment document HTML template", e);
        }
    }

    private String buildHtmlFromTemplate(Payment payment, User user, List<Payment> refunds, String documentNumber) {
        String template = loadTemplate();

        boolean isSuccess = (payment.getStatus() == PaymentStatus.SUCCESS);
        boolean isRefund = (payment.getTransactionType() == TransactionType.REFUND);

        String title;
        String badgeText;
        String badgeColor;

        if (isRefund) {
            title = "REFUND RECEIPT / CREDIT NOTE";
            badgeText = "REFUND PROCESSED";
            badgeColor = "#f97316";
        } else if (isSuccess) {
            title = "PAYMENT RECEIPT";
            badgeText = "PAID / SUCCESS";
            badgeColor = "#16a34a";
        } else {
            title = "PAYMENT STATEMENT (UNSUCCESSFUL)";
            badgeText = "FAILED — NOT PAID";
            badgeColor = "#dc2626";
        }

        String payerName = escape(user != null && user.getName() != null && !user.getName().isBlank() ? user.getName() : "Library Member");
        String payerEmail = escape(user != null && user.getEmail() != null ? user.getEmail() : "N/A");
        String memberId = escape(user != null && user.getUuid() != null ? user.getUuid() : "N/A");

        String aadhaarNumber = (user != null && user.getAadhaarLast4() != null && !user.getAadhaarLast4().isBlank())
                ? "XXXX XXXX " + user.getAadhaarLast4().trim()
                : "N/A";

        String formattedDate = payment.getCreatedAt() != null
                ? payment.getCreatedAt().format(DATE_TIME_FORMATTER)
                : LocalDate.now().toString();

        BigDecimal amountInRupees = BigDecimal.valueOf(payment.getAmount() != null ? payment.getAmount() : 0L)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        String paymentTypeLabel = switch (payment.getPaymentType()) {
            case MEMBERSHIP -> "1-Year Library Membership Security Deposit";
            case FINE -> "Overdue Book Fine Settlement" + (payment.getReferenceId() != null ? " (Fine #" + escape(payment.getReferenceId()) + ")" : "");
            default -> "Library Digital Services";
        };

        String orderId = escape(Objects.requireNonNullElse(payment.getRazorpayOrderId(), "N/A"));
        String paymentId = escape(Objects.requireNonNullElse(payment.getRazorpayPaymentId(), isSuccess ? "N/A" : "Not Captured"));
        String refundId = escape(Objects.requireNonNullElse(payment.getRazorpayRefundId(), "N/A"));

        String refundInfoItem = (isRefund && !refundId.equals("N/A"))
                ? "<div class=\"info-item\"><span class=\"info-label\">Refund ID:</span> <span class=\"info-value\" style=\"font-family: monospace;\">" + refundId + "</span></div>"
                : "";

        // Build refund section if any refunds exist on a successful payment
        StringBuilder refundsHtml = new StringBuilder();
        if (isSuccess && refunds != null && !refunds.isEmpty()) {
            refundsHtml.append("""
                <div class="section-title">Associated Refund Activity</div>
                <table class="items-table mb-4">
                    <thead>
                        <tr>
                            <th>Refund Reference</th>
                            <th>Date</th>
                            <th>Status</th>
                            <th style="text-align: right;">Refund Amount</th>
                        </tr>
                    </thead>
                    <tbody>
            """);

            long totalRefundedPaise = 0L;
            for (Payment r : refunds) {
                long rAmount = (r.getAmount() != null) ? r.getAmount() : 0L;
                if (r.getStatus() == PaymentStatus.SUCCESS) {
                    totalRefundedPaise += rAmount;
                }
                BigDecimal rInRupees = BigDecimal.valueOf(rAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                String rDate = r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_TIME_FORMATTER) : "N/A";
                refundsHtml.append(String.format("""
                        <tr>
                            <td>%s</td>
                            <td>%s</td>
                            <td><span class="status-badge" style="background: #ffedd5; color: #c2410c;">%s</span></td>
                            <td style="text-align: right; color: #ea580c; font-weight: bold;">-₹%s</td>
                        </tr>
                """, escape(Objects.requireNonNullElse(r.getRazorpayRefundId(), r.getUuid())), rDate, r.getStatus(), rInRupees));
            }

            BigDecimal netPaidRupees = BigDecimal.valueOf(Math.max(0L, (payment.getAmount() != null ? payment.getAmount() : 0L) - totalRefundedPaise))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            refundsHtml.append(String.format("""
                    </tbody>
                </table>
                <div class="totals-table-wrapper">
                    <table class="totals-table">
                        <tr>
                            <td><strong>Original Payment:</strong></td>
                            <td style="text-align: right;">₹%s</td>
                        </tr>
                        <tr>
                            <td><strong>Total Refunded:</strong></td>
                            <td style="text-align: right; color: #ea580c;">-₹%s</td>
                        </tr>
                        <tr class="grand-total-row">
                            <td><strong>Net Retained Amount:</strong></td>
                            <td style="text-align: right; color: #16a34a;">₹%s</td>
                        </tr>
                    </table>
                </div>
            """, amountInRupees, BigDecimal.valueOf(totalRefundedPaise).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP), netPaidRupees));
        }

        // Main totals breakdown
        String totalsRowHtml;
        if (isSuccess) {
            totalsRowHtml = String.format("""
                <tr>
                    <td><strong>Subtotal:</strong></td>
                    <td style="text-align: right;">₹%s</td>
                </tr>
                <tr>
                    <td><strong>Taxes &amp; Fees:</strong></td>
                    <td style="text-align: right;">₹0.00</td>
                </tr>
                <tr class="grand-total-row">
                    <td><strong>Total Paid (INR):</strong></td>
                    <td style="text-align: right; color: #16a34a;">₹%s</td>
                </tr>
            """, amountInRupees, amountInRupees);
        } else if (isRefund) {
            totalsRowHtml = String.format("""
                <tr class="grand-total-row">
                    <td><strong>Total Refunded (INR):</strong></td>
                    <td style="text-align: right; color: #ea580c;">-₹%s</td>
                </tr>
            """, amountInRupees);
        } else {
            totalsRowHtml = String.format("""
                <tr>
                    <td><strong>Amount Attempted:</strong></td>
                    <td style="text-align: right;">₹%s</td>
                </tr>
                <tr class="grand-total-row" style="background: #fee2e2;">
                    <td><strong style="color: #991b1b;">Amount Received:</strong></td>
                    <td style="text-align: right; font-weight: bold; color: #991b1b;">₹0.00</td>
                </tr>
                <tr>
                    <td><strong style="color: #64748b;">Outstanding Balance:</strong></td>
                    <td style="text-align: right; color: #64748b;">₹%s</td>
                </tr>
            """, amountInRupees, amountInRupees);
        }

        return template
                .replace("{{DOC_TITLE}}", escape(title))
                .replace("{{DOC_NUMBER}}", escape(documentNumber))
                .replace("{{STATUS_BADGE_TEXT}}", escape(badgeText))
                .replace("{{STATUS_BADGE_BG}}", badgeColor)
                .replace("{{PAYER_NAME}}", payerName)
                .replace("{{PAYER_EMAIL}}", payerEmail)
                .replace("{{MEMBER_ID}}", memberId)
                .replace("{{AADHAAR_NUMBER}}", escape(aadhaarNumber))
                .replace("{{TRANSACTION_DATE}}", formattedDate)
                .replace("{{ORDER_ID}}", orderId)
                .replace("{{PAYMENT_ID}}", paymentId)
                .replace("{{REFUND_INFO_ITEM}}", refundInfoItem)
                .replace("{{ITEM_DESCRIPTION}}", escape(paymentTypeLabel))
                .replace("{{ITEM_TYPE}}", escape(payment.getPaymentType().name()))
                .replace("{{TRANSACTION_TYPE}}", escape(payment.getTransactionType().name()))
                .replace("{{ITEM_AMOUNT}}", amountInRupees.toString())
                .replace("{{TOTALS_SECTION}}", totalsRowHtml)
                .replace("{{REFUNDS_SECTION}}", refundsHtml.toString());
    }

    private String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
