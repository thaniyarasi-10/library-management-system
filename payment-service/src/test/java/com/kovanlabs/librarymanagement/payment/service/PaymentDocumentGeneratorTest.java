package com.kovanlabs.librarymanagement.payment.service;

import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.database.enums.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentDocumentGeneratorTest {

    private PaymentDocumentGenerator generator;
    private User testUser;

    @BeforeEach
    void setUp() {
        generator = new PaymentDocumentGenerator();
        testUser = User.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Jane Doe & Co")
                .email("jane.doe@example.com")
                .build();
    }

    @Test
    void testGenerateDocumentNumber_existingNumberPreserved() {
        Payment payment = Payment.builder()
                .documentNumber("EXISTING-DOC-12345")
                .build();

        String docNumber = generator.generateDocumentNumber(payment);

        assertEquals("EXISTING-DOC-12345", docNumber);
    }

    @Test
    void testGenerateDocumentNumber_successPayment() {
        Payment payment = Payment.builder()
                .uuid("a1b2c3d4-1111-2222-3333-444455556666")
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.of(2026, 10, 23, 10, 0))
                .build();

        String docNumber = generator.generateDocumentNumber(payment);

        assertNotNull(docNumber);
        assertTrue(docNumber.startsWith("REC-202610-A1B2C3D4"));
    }

    @Test
    void testGenerateDocumentNumber_failedPayment() {
        Payment payment = Payment.builder()
                .uuid("f1f2f3f4-1111-2222-3333-444455556666")
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.FAILED)
                .createdAt(LocalDateTime.of(2026, 10, 23, 10, 0))
                .build();

        String docNumber = generator.generateDocumentNumber(payment);

        assertNotNull(docNumber);
        assertTrue(docNumber.startsWith("STMT-202610-F1F2F3F4"));
    }

    @Test
    void testGenerateDocumentNumber_refundTransaction() {
        Payment payment = Payment.builder()
                .uuid("r1r2r3r4-1111-2222-3333-444455556666")
                .transactionType(TransactionType.REFUND)
                .status(PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.of(2026, 10, 23, 10, 0))
                .build();

        String docNumber = generator.generateDocumentNumber(payment);

        assertNotNull(docNumber);
        assertTrue(docNumber.startsWith("REF-202610-R1R2R3R4"));
    }

    @Test
    void testGeneratePdf_successPaymentReceipt() throws Exception {
        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(testUser.getUuid())
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .razorpayOrderId("order_test_123")
                .razorpayPaymentId("pay_test_456")
                .createdAt(LocalDateTime.now())
                .build();

        String docNumber = generator.generateDocumentNumber(payment);
        byte[] pdfBytes = generator.generatePdf(payment, testUser, List.of(), docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        // Verify standard PDF header %PDF-
        assertEquals('%', (char) pdfBytes[0]);
        assertEquals('P', (char) pdfBytes[1]);
        assertEquals('D', (char) pdfBytes[2]);
        assertEquals('F', (char) pdfBytes[3]);
    }

    @Test
    void testGeneratePdf_failedPaymentStatement() throws Exception {
        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(testUser.getUuid())
                .paymentType(PaymentType.FINE)
                .referenceId("101")
                .transactionType(TransactionType.PAYMENT)
                .amount(2500L)
                .status(PaymentStatus.FAILED)
                .razorpayOrderId("order_fail_789")
                .razorpayPaymentId("pay_fail_000")
                .createdAt(LocalDateTime.now())
                .build();

        String docNumber = generator.generateDocumentNumber(payment);
        byte[] pdfBytes = generator.generatePdf(payment, testUser, List.of(), docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        assertEquals('%', (char) pdfBytes[0]);
    }

    @Test
    void testGeneratePdf_withAssociatedRefunds() throws Exception {
        Payment original = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(testUser.getUuid())
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .razorpayOrderId("order_orig_1")
                .razorpayPaymentId("pay_orig_1")
                .createdAt(LocalDateTime.now().minusDays(5))
                .build();

        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(testUser.getUuid())
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.REFUND)
                .amount(10000L)
                .status(PaymentStatus.SUCCESS)
                .razorpayRefundId("rfnd_001")
                .createdAt(LocalDateTime.now())
                .build();

        String docNumber = generator.generateDocumentNumber(original);
        byte[] pdfBytes = generator.generatePdf(original, testUser, List.of(refund), docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}
