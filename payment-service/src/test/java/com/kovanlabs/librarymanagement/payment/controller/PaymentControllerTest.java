package com.kovanlabs.librarymanagement.payment.controller;

import com.kovanlabs.librarymanagement.payment.dto.MembershipCancellationRefundResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentOrderResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.VerifyPaymentRequest;
import com.kovanlabs.librarymanagement.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private Principal principal;

    @InjectMocks
    private PaymentController paymentController;

    private final String email = "user@example.com";

    @BeforeEach
    void setUp() {
        lenient().when(principal.getName()).thenReturn(email);
    }

    @Test
    void testCreateMembershipOrder() {
        PaymentOrderResponse response = new PaymentOrderResponse(
                "order_123",
                50000L,
                "rzp_key_test",
                "MEMBERSHIP",
                "mem_ref_1"
        );
        when(paymentService.createMembershipOrder(email)).thenReturn(response);

        ResponseEntity<PaymentOrderResponse> result = paymentController.createMembershipOrder(principal);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(paymentService).createMembershipOrder(email);
    }

    @Test
    void testCreateFineOrder() {
        Long fineId = 55L;
        PaymentOrderResponse response = new PaymentOrderResponse(
                "order_fine_55",
                2500L,
                "rzp_key_test",
                "FINE",
                "55"
        );
        when(paymentService.createFineOrder(fineId, email)).thenReturn(response);

        ResponseEntity<PaymentOrderResponse> result = paymentController.createFineOrder(fineId, principal);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(paymentService).createFineOrder(fineId, email);
    }

    @Test
    void testVerifyPayment() {
        VerifyPaymentRequest request = new VerifyPaymentRequest("order_123", "pay_123", "sig_123");
        PaymentResponseDto response = new PaymentResponseDto(
                UUID.randomUUID().toString(),
                1L,
                "user-uuid-1",
                "PAYMENT",
                "MEMBERSHIP",
                "order_123",
                "pay_123",
                null,
                null,
                "mem-uuid-1",
                50000L,
                "SUCCESS",
                null,
                null,
                List.of()
        );

        when(paymentService.verifyPayment(request, email)).thenReturn(response);

        ResponseEntity<PaymentResponseDto> result = paymentController.verifyPayment(request, principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(paymentService).verifyPayment(request, email);
    }

    @Test
    void testCancelMembershipWithRefund() {
        MembershipCancellationRefundResponse response = new MembershipCancellationRefundResponse(
                true,
                50000L,
                1000L,
                49000L,
                "rfnd_123",
                "Membership cancelled"
        );

        when(paymentService.cancelMembershipWithRefund(email)).thenReturn(response);

        ResponseEntity<MembershipCancellationRefundResponse> result = paymentController.cancelMembershipWithRefund(principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(paymentService).cancelMembershipWithRefund(email);
    }

    @Test
    void testGetUserPayments() {
        PaymentResponseDto dto = new PaymentResponseDto(
                UUID.randomUUID().toString(),
                1L,
                "user-uuid-1",
                "PAYMENT",
                "MEMBERSHIP",
                "order_123",
                "pay_123",
                null,
                null,
                "mem-uuid-1",
                50000L,
                "SUCCESS",
                null,
                null,
                List.of()
        );

        when(paymentService.getUserPayments(email)).thenReturn(List.of(dto));

        ResponseEntity<List<PaymentResponseDto>> result = paymentController.getUserPayments(principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        verify(paymentService).getUserPayments(email);
    }

    @Test
    void testVerifyPayment_failed() {
        VerifyPaymentRequest request = new VerifyPaymentRequest("order_fail_1", "pay_fail_1", null);
        PaymentResponseDto response = new PaymentResponseDto(
                UUID.randomUUID().toString(),
                1L,
                "user-uuid-1",
                "PAYMENT",
                "FINE",
                "order_fail_1",
                "pay_fail_1",
                null,
                null,
                "55",
                2500L,
                "FAILED",
                null,
                null,
                List.of()
        );

        when(paymentService.verifyPayment(request, email)).thenReturn(response);

        ResponseEntity<PaymentResponseDto> result = paymentController.verifyPayment(request, principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
        assertEquals("FAILED", result.getBody().status());
        verify(paymentService).verifyPayment(request, email);
    }
}
