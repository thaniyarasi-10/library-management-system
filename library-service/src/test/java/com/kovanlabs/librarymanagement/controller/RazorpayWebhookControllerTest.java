package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RazorpayWebhookControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private RazorpayWebhookController razorpayWebhookController;

    @Test
    void testHandleRazorpayWebhook_validSignature_returns200() {
        String payload = "{\"event\":\"payment.captured\"}";
        String sig = "valid_signature";

        doNothing().when(paymentService).handleWebhook(payload, sig);

        ResponseEntity<Map<String, String>> response =
                razorpayWebhookController.handleRazorpayWebhook(payload, sig);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("success", response.getBody().get("status"));
        verify(paymentService).handleWebhook(payload, sig);
    }

    @Test
    void testHandleRazorpayWebhook_invalidSignature_throwsBadRequest() {
        String payload = "{\"event\":\"payment.captured\"}";
        String sig = "invalid_signature";

        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook signature"))
                .when(paymentService).handleWebhook(payload, sig);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> razorpayWebhookController.handleRazorpayWebhook(payload, sig));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testHandleRazorpayWebhook_missingSignature_throwsBadRequest() {
        String payload = "{\"event\":\"payment.captured\"}";

        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing X-Razorpay-Signature header"))
                .when(paymentService).handleWebhook(payload, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> razorpayWebhookController.handleRazorpayWebhook(payload, null));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void testHandleRazorpayWebhook_duplicateEvent_handledIdempotently() {
        String payload = "{\"event\":\"payment.captured\"}";
        String sig = "valid_signature";

        doNothing().when(paymentService).handleWebhook(payload, sig);

        // First delivery
        ResponseEntity<Map<String, String>> first =
                razorpayWebhookController.handleRazorpayWebhook(payload, sig);
        assertEquals(HttpStatus.OK, first.getStatusCode());

        // Duplicate delivery
        ResponseEntity<Map<String, String>> duplicate =
                razorpayWebhookController.handleRazorpayWebhook(payload, sig);
        assertEquals(HttpStatus.OK, duplicate.getStatusCode());

        verify(paymentService, times(2)).handleWebhook(payload, sig);
    }
}
