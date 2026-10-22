package com.kovanlabs.librarymanagement.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.payment.config.RazorpayProperties;
import com.kovanlabs.librarymanagement.payment.dto.RazorpayOrderDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RazorpayClientServiceTest {

    @Mock
    private RestClient.Builder restClientBuilder;

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private RazorpayProperties razorpayProperties;
    private ObjectMapper objectMapper;
    private RazorpayClientService razorpayClientService;

    private final String keySecret = "rzp_test_secret_12345";
    private final String webhookSecret = "rzp_webhook_secret_67890";

    @BeforeEach
    void setUp() {
        razorpayProperties = new RazorpayProperties();
        razorpayProperties.setKeyId("rzp_test_key_123");
        razorpayProperties.setKeySecret(keySecret);
        razorpayProperties.setWebhookSecret(webhookSecret);
        razorpayProperties.setBaseUrl("https://api.razorpay.com/v1");

        objectMapper = new ObjectMapper();

        lenient().when(restClientBuilder.baseUrl(anyString())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.defaultHeaders(any())).thenReturn(restClientBuilder);
        lenient().when(restClientBuilder.build()).thenReturn(restClient);

        razorpayClientService = new RazorpayClientService(razorpayProperties, restClientBuilder, objectMapper);
    }

    private String calculateHmac(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Test
    void testVerifyPaymentSignature_valid() throws Exception {
        String orderId = "order_N1234567890";
        String paymentId = "pay_P1234567890";
        String validSig = calculateHmac(orderId + "|" + paymentId, keySecret);

        boolean result = razorpayClientService.verifyPaymentSignature(orderId, paymentId, validSig);

        assertTrue(result);
    }

    @Test
    void testVerifyPaymentSignature_invalid() {
        String orderId = "order_N1234567890";
        String paymentId = "pay_P1234567890";
        String invalidSig = "completely_wrong_signature";

        boolean result = razorpayClientService.verifyPaymentSignature(orderId, paymentId, invalidSig);

        assertFalse(result);
    }

    @Test
    void testVerifyPaymentSignature_nullParameters() {
        assertFalse(razorpayClientService.verifyPaymentSignature(null, "pay_1", "sig"));
        assertFalse(razorpayClientService.verifyPaymentSignature("order_1", null, "sig"));
        assertFalse(razorpayClientService.verifyPaymentSignature("order_1", "pay_1", null));
    }

    @Test
    void testVerifyWebhookSignature_valid() throws Exception {
        String payload = "{\"event\":\"order.paid\",\"payload\":{}}";
        String validSig = calculateHmac(payload, webhookSecret);

        boolean result = razorpayClientService.verifyWebhookSignature(payload, validSig);

        assertTrue(result);
    }

    @Test
    void testVerifyWebhookSignature_invalid() {
        String payload = "{\"event\":\"order.paid\",\"payload\":{}}";
        String invalidSig = "invalid_webhook_sig";

        boolean result = razorpayClientService.verifyWebhookSignature(payload, invalidSig);

        assertFalse(result);
    }

    @Test
    void testVerifyWebhookSignature_nullParameters() {
        assertFalse(razorpayClientService.verifyWebhookSignature(null, "sig"));
        assertFalse(razorpayClientService.verifyWebhookSignature("payload", null));
    }

    @Test
    void testCreateOrder_success() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/orders"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"id\":\"order_K12345678\",\"amount\":50000,\"currency\":\"INR\"}");

        String orderId = razorpayClientService.createOrder(50000L, "rec_123", Map.of("user", "test"));

        assertEquals("order_K12345678", orderId);
    }

    @Test
    void testCreateOrder_missingId_throwsException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/orders"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"error\":\"something went wrong\"}");

        assertThrows(ResponseStatusException.class,
                () -> razorpayClientService.createOrder(50000L, "rec_123", null));
    }

    @Test
    void testCreateOrder_networkFailure_throwsBadGateway() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/orders"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(new RuntimeException("Connection refused"));

        assertThrows(ResponseStatusException.class,
                () -> razorpayClientService.createOrder(50000L, "rec_123", Map.of()));
    }

    @Test
    void testRefundPayment_success() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/payments/pay_123/refund"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"id\":\"rfnd_987654321\",\"amount\":50000}");

        String refundId = razorpayClientService.refundPayment("pay_123", 50000L);

        assertEquals("rfnd_987654321", refundId);
    }

    @Test
    void testRefundPayment_missingId_throwsException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/payments/pay_123/refund"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"error\":\"payment not found\"}");

        assertThrows(ResponseStatusException.class,
                () -> razorpayClientService.refundPayment("pay_123", 50000L));
    }

    @Test
    void testCreateOrder_nullBody_throwsException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/orders"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> razorpayClientService.createOrder(50000L, "rec_null", Map.of()));
    }

    @Test
    void testRefundPayment_nullBody_throwsException() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/payments/pay_123/refund"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(null);

        assertThrows(ResponseStatusException.class,
                () -> razorpayClientService.refundPayment("pay_123", 50000L));
    }

    @Test
    void testRefundPayment_networkError_throwsBadGateway() {
        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/payments/pay_123/refund"))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(any())).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(Map.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenThrow(new RuntimeException("Timeout"));

        assertThrows(ResponseStatusException.class,
                () -> razorpayClientService.refundPayment("pay_123", 50000L));
    }

    @Test
    void testFetchOrder_success() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/orders/{id}"), eq("order_fetch_1"))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn("{\"id\":\"order_fetch_1\",\"amount\":50000,\"notes\":{\"user_uuid\":\"u-1\",\"payment_type\":\"MEMBERSHIP\"}}");

        RazorpayOrderDetails details = razorpayClientService.fetchOrder("order_fetch_1");

        assertNotNull(details);
        assertEquals("order_fetch_1", details.id());
        assertEquals(50000L, details.amount());
        assertEquals("u-1", details.notes().get("user_uuid"));
        assertEquals("MEMBERSHIP", details.notes().get("payment_type"));
    }

    @Test
    void testFetchOrder_networkError_throwsException() {
        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/orders/{id}"), eq("order_err"))).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.retrieve()).thenThrow(new RuntimeException("Connection error"));

        assertThrows(ResponseStatusException.class,
                () -> razorpayClientService.fetchOrder("order_err"));
    }
}
