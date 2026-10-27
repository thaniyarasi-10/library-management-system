package com.kovanlabs.librarymanagement.payment.mapping;

import com.kovanlabs.librarymanagement.database.entity.Payment;
import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.database.enums.TransactionType;
import com.kovanlabs.librarymanagement.payment.dto.PaymentHistoryResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentMapperTest {

    private final PaymentMapper mapper = PaymentMapper.INSTANCE;

    @Test
    void testMapToResponse_singlePayment() {
        Payment payment = Payment.builder()
                .uuid("pay-uuid-1")
                .id(100L)
                .userUuid("user-1")
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.MEMBERSHIP)
                .razorpayOrderId("order_1")
                .razorpayPaymentId("pay_1")
                .amount(50000L)
                .status(PaymentStatus.SUCCESS)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        PaymentResponseDto dto = mapper.mapToResponse(payment);

        assertNotNull(dto);
        assertEquals("pay-uuid-1", dto.uuid());
        assertEquals(100L, dto.id());
        assertEquals("user-1", dto.userUuid());
        assertEquals("PAYMENT", dto.transactionType());
        assertEquals("MEMBERSHIP", dto.paymentType());
        assertEquals("order_1", dto.razorpayOrderId());
        assertEquals("pay_1", dto.razorpayPaymentId());
        assertEquals(50000L, dto.amount());
        assertEquals("SUCCESS", dto.status());
        assertNotNull(dto.refunds());
        assertTrue(dto.refunds().isEmpty());
    }

    @Test
    void testMapToResponse_withRefunds() {
        Payment payment = Payment.builder()
                .uuid("pay-uuid-2")
                .userUuid("user-2")
                .transactionType(TransactionType.PAYMENT)
                .paymentType(PaymentType.FINE)
                .amount(2000L)
                .status(PaymentStatus.SUCCESS)
                .build();

        PaymentResponseDto refundDto = new PaymentResponseDto(
                "rfnd-uuid", 200L, "user-2", "REFUND", "FINE",
                null, null, "rfnd_123", "pay-uuid-2", "pay_2",
                1000L, "SUCCESS",
                null, null, List.of()
        );

        PaymentResponseDto dto = mapper.mapToResponse(payment, List.of(refundDto));

        assertNotNull(dto);
        assertEquals("pay-uuid-2", dto.uuid());
        assertEquals("SUCCESS", dto.status());
        assertEquals(1, dto.refunds().size());
        assertEquals("rfnd-uuid", dto.refunds().get(0).uuid());
    }

    @Test
    void testMapToResponseList() {
        Payment p1 = Payment.builder().uuid("p-1").amount(100L).status(PaymentStatus.SUCCESS).build();
        Payment p2 = Payment.builder().uuid("p-2").amount(200L).status(PaymentStatus.FAILED).build();

        List<PaymentResponseDto> list = mapper.mapToResponseList(List.of(p1, p2));

        assertNotNull(list);
        assertEquals(2, list.size());
        assertEquals("p-1", list.get(0).uuid());
        assertEquals("p-2", list.get(1).uuid());
    }

    @Test
    void testCreateRefundEntity() {
        Payment original = Payment.builder()
                .uuid("orig-uuid")
                .userUuid("user-orig")
                .paymentType(PaymentType.MEMBERSHIP)
                .amount(50000L)
                .build();

        Payment refund = mapper.createRefundEntity(
                original,
                "rfnd_rzp_99",
                "pay_rzp_88",
                25000L,
                PaymentStatus.SUCCESS
        );

        assertNotNull(refund);
        assertNotNull(refund.getUuid());
        assertEquals("user-orig", refund.getUserUuid());
        assertEquals(TransactionType.REFUND, refund.getTransactionType());
        assertEquals(PaymentType.MEMBERSHIP, refund.getPaymentType());
        assertEquals("rfnd_rzp_99", refund.getRazorpayRefundId());
        assertEquals("orig-uuid", refund.getPaymentUuid());
        assertEquals("pay_rzp_88", refund.getReferenceId());
        assertEquals(25000L, refund.getAmount());
        assertEquals(PaymentStatus.SUCCESS, refund.getStatus());
        assertNotNull(refund.getCreatedAt());
        assertNotNull(refund.getUpdatedAt());
    }

    @Test
    void testCreatePaymentEntity() {
        Payment payment = mapper.createPaymentEntity(
                "user-abc",
                PaymentType.MEMBERSHIP,
                "order_rzp_123",
                "pay_rzp_456",
                "ref_789",
                50000L,
                PaymentStatus.SUCCESS
        );

        assertNotNull(payment);
        assertNotNull(payment.getUuid());
        assertEquals("user-abc", payment.getUserUuid());
        assertEquals(PaymentType.MEMBERSHIP, payment.getPaymentType());
        assertEquals(TransactionType.PAYMENT, payment.getTransactionType());
        assertEquals("order_rzp_123", payment.getRazorpayOrderId());
        assertEquals("pay_rzp_456", payment.getRazorpayPaymentId());
        assertEquals("ref_789", payment.getReferenceId());
        assertEquals(50000L, payment.getAmount());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertNull(payment.getRazorpayRefundId());
        assertNull(payment.getPaymentUuid());
        assertNotNull(payment.getCreatedAt());
        assertNotNull(payment.getUpdatedAt());
    }

    @Test
    void testMapToHistoryResponse() {
        Payment p = Payment.builder().uuid("p-1").build();
        Page<Payment> page = new PageImpl<>(List.of(p), PageRequest.of(0, 10), 1L);
        PaymentResponseDto dto = new PaymentResponseDto(
                "p-1", 1L, "u-1", "PAYMENT", "MEMBERSHIP",
                "o-1", "pay-1", null, null, "ref-1", 50000L, "SUCCESS",
                null, null, List.of()
        );

        PaymentHistoryResponseDto response = mapper.mapToHistoryResponse(
                50000L,
                0L,
                50000L,
                1L,
                List.of(dto),
                page
        );

        assertNotNull(response);
        assertEquals(50000L, response.totalPaid());
        assertEquals(0L, response.totalRefunded());
        assertEquals(50000L, response.netPaid());
        assertEquals(1L, response.successfulTransactionCount());
        assertEquals(1, response.content().size());
        assertEquals(0, response.currentPage());
        assertEquals(10, response.pageSize());
        assertEquals(1L, response.totalElements());
        assertEquals(1, response.totalPages());
        assertTrue(response.isFirst());
        assertTrue(response.isLast());
        assertFalse(response.hasNext());
        assertFalse(response.hasPrevious());
    }

    @Test
    void testNullMappings() {
        assertNull(mapper.mapToResponse(null));
        assertNull(mapper.mapToResponse(null, null));
        assertNull(mapper.mapToResponseList(null));
        assertNull(mapper.createRefundEntity(null, null, null, null, null));
        assertNull(mapper.mapToHistoryResponse(null, null, null, 0L, null, null));
    }
}
