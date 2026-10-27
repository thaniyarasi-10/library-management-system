package com.kovanlabs.librarymanagement.payment.controller;

import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.payment.dto.PaymentHistoryResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import com.kovanlabs.librarymanagement.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPaymentHistoryControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private Principal principal;

    @InjectMocks
    private UserPaymentHistoryController controller;

    private PaymentHistoryResponseDto sampleResponse;

    @BeforeEach
    void setUp() {
        PaymentResponseDto item = new PaymentResponseDto(
                "item-uuid-1", 1L, "user-uuid-1", "PAYMENT", "MEMBERSHIP",
                "order_123", "pay_123", null, null, "ref_123",
                50000L, "SUCCESS",
                LocalDateTime.now(), LocalDateTime.now(), List.of()
        );
        sampleResponse = new PaymentHistoryResponseDto(
                50000L, 0L, 50000L, 1L,
                List.of(item), 0, 10, 1L, 1, true, true, false, false
        );
    }

    @Test
    void testGetPaymentHistory_success() {
        when(principal.getName()).thenReturn("alice@example.com");
        when(paymentService.getPaymentHistory("alice@example.com", PaymentType.MEMBERSHIP, PaymentStatus.SUCCESS, "order", 0, 10))
                .thenReturn(sampleResponse);

        ResponseEntity<PaymentHistoryResponseDto> response = controller.getPaymentHistory(
                PaymentType.MEMBERSHIP, PaymentStatus.SUCCESS, "order", 0, 10, principal
        );

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().content().size());
        assertEquals(50000L, response.getBody().totalPaid());
        verify(paymentService).getPaymentHistory("alice@example.com", PaymentType.MEMBERSHIP, PaymentStatus.SUCCESS, "order", 0, 10);
    }

    @Test
    void testGetPaymentHistory_defaultParameters() {
        when(principal.getName()).thenReturn("alice@example.com");
        when(paymentService.getPaymentHistory(eq("alice@example.com"), eq(null), eq(null), eq(null), anyInt(), anyInt()))
                .thenReturn(sampleResponse);

        ResponseEntity<PaymentHistoryResponseDto> response = controller.getPaymentHistory(
                null, null, null, 0, 10, principal
        );

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(paymentService).getPaymentHistory("alice@example.com", null, null, null, 0, 10);
    }

    @Test
    void testGetPaymentHistory_unauthorizedWhenPrincipalNull() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getPaymentHistory(null, null, null, 0, 10, null)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void testGetPaymentHistory_unauthorizedWhenPrincipalNameBlank() {
        when(principal.getName()).thenReturn("   ");
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getPaymentHistory(null, null, null, 0, 10, principal)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    void testGetPaymentHistory_unauthorizedWhenPrincipalNameNull() {
        when(principal.getName()).thenReturn(null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.getPaymentHistory(null, null, null, 0, 10, principal)
        );
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }
}
