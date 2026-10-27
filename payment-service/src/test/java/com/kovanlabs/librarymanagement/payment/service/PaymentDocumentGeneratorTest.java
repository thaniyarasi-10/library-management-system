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

    @Test
    void testGenerateDocumentNumber_blankNumberAndNullCreatedAtAndShortUuid() {
        Payment payment = Payment.builder()
                .documentNumber("   ")
                .uuid("abc")
                .createdAt(null)
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.SUCCESS)
                .build();

        String docNumber = generator.generateDocumentNumber(payment);

        assertNotNull(docNumber);
        assertTrue(docNumber.startsWith("REC-"));
        assertTrue(docNumber.endsWith("-ABC"));
    }

    @Test
    void testGenerateDocumentNumber_nullUuid() {
        Payment payment = Payment.builder()
                .documentNumber(null)
                .uuid(null)
                .createdAt(LocalDateTime.now())
                .transactionType(TransactionType.PAYMENT)
                .status(PaymentStatus.FAILED)
                .build();

        String docNumber = generator.generateDocumentNumber(payment);

        assertNotNull(docNumber);
        assertTrue(docNumber.startsWith("STMT-"));
    }

    @Test
    void testGeneratePdf_refundReceiptWithRefundId() throws Exception {
        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(testUser.getUuid())
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.REFUND)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .razorpayRefundId("rfnd_test_999")
                .createdAt(LocalDateTime.now())
                .build();

        String docNumber = generator.generateDocumentNumber(refund);
        byte[] pdfBytes = generator.generatePdf(refund, testUser, List.of(), docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void testGeneratePdf_refundReceiptWithoutRefundId() throws Exception {
        Payment refund = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .userUuid(testUser.getUuid())
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.REFUND)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .razorpayRefundId(null)
                .createdAt(LocalDateTime.now())
                .build();

        String docNumber = generator.generateDocumentNumber(refund);
        byte[] pdfBytes = generator.generatePdf(refund, testUser, null, docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void testGeneratePdf_failedPaymentWithNullPaymentIdAndNullOrderId() throws Exception {
        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .paymentType(PaymentType.FINE)
                .referenceId(null)
                .transactionType(TransactionType.PAYMENT)
                .amount(null)
                .status(PaymentStatus.FAILED)
                .razorpayOrderId(null)
                .razorpayPaymentId(null)
                .createdAt(null)
                .build();

        String docNumber = generator.generateDocumentNumber(payment);
        byte[] pdfBytes = generator.generatePdf(payment, null, List.of(), docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void testGeneratePdf_userWithNullOrBlankFields() throws Exception {
        User blankUser = User.builder()
                .uuid(null)
                .name("   ")
                .email(null)
                .build();

        Payment payment = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .paymentType(PaymentType.FINE)
                .referenceId("202")
                .transactionType(TransactionType.PAYMENT)
                .amount(1500L)
                .status(PaymentStatus.SUCCESS)
                .razorpayOrderId("order_202")
                .razorpayPaymentId(null)
                .createdAt(LocalDateTime.now())
                .build();

        String docNumber = generator.generateDocumentNumber(payment);
        byte[] pdfBytes = generator.generatePdf(payment, blankUser, List.of(), docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }

    @Test
    void testGeneratePdf_withRefundItemsHavingNullFieldsAndNonSuccessStatus() throws Exception {
        Payment original = Payment.builder()
                .uuid(UUID.randomUUID().toString())
                .paymentType(PaymentType.MEMBERSHIP)
                .transactionType(TransactionType.PAYMENT)
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();

        Payment failedRefund = Payment.builder()
                .uuid("rf-failed-uuid")
                .amount(null)
                .status(PaymentStatus.FAILED)
                .createdAt(null)
                .razorpayRefundId(null)
                .build();

        String docNumber = generator.generateDocumentNumber(original);
        byte[] pdfBytes = generator.generatePdf(original, testUser, List.of(failedRefund), docNumber);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
    }
}
