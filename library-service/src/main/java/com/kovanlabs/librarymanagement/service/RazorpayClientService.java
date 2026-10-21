package com.kovanlabs.librarymanagement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.config.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class RazorpayClientService {

    private final RazorpayProperties razorpayProperties;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    private RestClient getClient() {
        return restClientBuilder
                .baseUrl(razorpayProperties.getBaseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(
                        razorpayProperties.getKeyId(),
                        razorpayProperties.getKeySecret()))
                .build();
    }

    /**
     * Creates an order with Razorpay in Test Mode.
     *
     * @param amountInPaise Amount in paise (e.g. 50000 for ₹500)
     * @param receipt Unique receipt identifier
     * @param notes Additional metadata notes
     * @return Razorpay Order ID (e.g. order_xxx)
     */
    public String createOrder(long amountInPaise, String receipt, Map<String, String> notes) {
        log.info("Creating Razorpay order for amount: {} paise, receipt: {}", amountInPaise, receipt);
        try {
            Map<String, Object> requestPayload = Map.of(
                    "amount", amountInPaise,
                    "currency", "INR",
                    "receipt", receipt,
                    "notes", Objects.requireNonNullElse(notes, Map.of())
            );

            String responseBody = getClient().post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            if (Objects.nonNull(responseBody)) {
                JsonNode root = objectMapper.readTree(responseBody);
                String orderId = root.path("id").asText(null);
                if (Objects.nonNull(orderId) && !orderId.isBlank()) {
                    log.info("Successfully created Razorpay order: {}", orderId);
                    return orderId;
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse Razorpay order ID");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay order creation failed: " + e.getMessage());
        }
    }

    /**
     * Initiates a refund for an existing payment through Razorpay Test Mode Refund API.
     *
     * @param paymentId The Razorpay Payment ID to refund
     * @param amountInPaise Amount in paise to refund
     * @return Razorpay Refund ID (e.g. rfnd_xxx)
     */
    public String refundPayment(String paymentId, long amountInPaise) {
        log.info("Initiating Razorpay refund for payment: {}, amount: {} paise", paymentId, amountInPaise);
        try {
            Map<String, Object> requestPayload = Map.of(
                    "amount", amountInPaise
            );

            String responseBody = getClient().post()
                    .uri("/payments/{paymentId}/refund", paymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            if (Objects.nonNull(responseBody)) {
                JsonNode root = objectMapper.readTree(responseBody);
                String refundId = root.path("id").asText(null);
                if (Objects.nonNull(refundId) && !refundId.isBlank()) {
                    log.info("Successfully processed Razorpay refund: {}", refundId);
                    return refundId;
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to parse Razorpay refund ID");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process Razorpay refund: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Razorpay refund failed: " + e.getMessage());
        }
    }

    /**
     * Verifies the Razorpay payment signature returned by Checkout.
     * Formula: HMAC-SHA256(order_id + "|" + payment_id, secret) == signature
     */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (Objects.isNull(orderId) || Objects.isNull(paymentId) || Objects.isNull(signature)) {
            return false;
        }
        String payload = orderId + "|" + paymentId;
        return calculateAndCompareHmac(payload, signature, razorpayProperties.getKeySecret());
    }

    /**
     * Verifies the webhook signature sent in X-Razorpay-Signature header.
     * Formula: HMAC-SHA256(rawBody, webhookSecret) == signature
     */
    public boolean verifyWebhookSignature(String rawPayload, String signature) {
        if (Objects.isNull(rawPayload) || Objects.isNull(signature) || signature.isBlank()) {
            return false;
        }
        String secret = razorpayProperties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            secret = System.getenv("RAZORPAY_WEBHOOK_SECRET");
        }
        if (secret == null || secret.isBlank()) {
            log.error("Razorpay webhook secret is not configured in properties or RAZORPAY_WEBHOOK_SECRET environment variable");
            return false;
        }
        return calculateAndCompareHmac(rawPayload, signature.trim(), secret);
    }

    private boolean calculateAndCompareHmac(String data, String expectedSignature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return MessageDigest.isEqual(
                    hexString.toString().getBytes(StandardCharsets.UTF_8),
                    expectedSignature.trim().toLowerCase().getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Failed to calculate HMAC-SHA256 signature: {}", e.getMessage());
            return false;
        }
    }
}
