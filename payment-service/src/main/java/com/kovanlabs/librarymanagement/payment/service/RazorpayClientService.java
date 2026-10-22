package com.kovanlabs.librarymanagement.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kovanlabs.librarymanagement.payment.config.RazorpayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.kovanlabs.librarymanagement.payment.dto.RazorpayOrderDetails;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
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

            JsonNode root = objectMapper.readTree(responseBody);
            String orderId = root.path("id").asText(null);
            if (Objects.isNull(orderId) || orderId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No order ID returned by Razorpay");
            }
            log.info("Created Razorpay order successfully with ID: {}", orderId);
            return orderId;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create Razorpay order: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to initialize payment gateway order: " + e.getMessage());
        }
    }

    /**
     * Fetches details of a previously created order from Razorpay.
     *
     * @param orderId Razorpay Order ID
     * @return RazorpayOrderDetails containing ID, amount in paise, and metadata notes
     */
    public RazorpayOrderDetails fetchOrder(String orderId) {
        log.info("Fetching Razorpay order details for orderId: {}", orderId);
        try {
            String responseBody = getClient().get()
                    .uri("/orders/{id}", orderId)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String id = root.path("id").asText(orderId);
            long amount = root.path("amount").asLong(0L);

            Map<String, String> notes = new HashMap<>();
            JsonNode notesNode = root.path("notes");
            if (notesNode != null && notesNode.isObject()) {
                notesNode.fieldNames().forEachRemaining(field -> notes.put(field, notesNode.path(field).asText("")));
            }
            return new RazorpayOrderDetails(id, amount, notes);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch Razorpay order {}: {}", orderId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to retrieve order from payment gateway: " + e.getMessage());
        }
    }

    /**
     * Verifies the cryptographic HMAC SHA256 signature returned after frontend checkout.
     *
     * @param orderId Razorpay Order ID
     * @param paymentId Razorpay Payment ID
     * @param signature Razorpay Signature
     * @return {@code true} if signature matches
     */
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        try {
            String data = orderId + "|" + paymentId;
            String generatedSignature = calculateHmacSha256(data, razorpayProperties.getKeySecret());
            return MessageDigest.isEqual(
                    generatedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error verifying payment signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Verifies Razorpay webhook X-Razorpay-Signature header against the raw webhook payload.
     *
     * @param payload Raw payload string
     * @param signatureHeader Received X-Razorpay-Signature header
     * @return {@code true} if valid
     */
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        try {
            String generatedSignature = calculateHmacSha256(payload, razorpayProperties.getWebhookSecret());
            return MessageDigest.isEqual(
                    generatedSignature.getBytes(StandardCharsets.UTF_8),
                    signatureHeader.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Issues a full or partial refund for a successful Razorpay payment.
     *
     * @param paymentId Razorpay Payment ID
     * @param amountInPaise Amount to refund in paise
     * @return Razorpay Refund ID
     */
    public String refundPayment(String paymentId, long amountInPaise) {
        log.info("Initiating Razorpay refund for paymentId: {}, amount: {} paise", paymentId, amountInPaise);
        try {
            Map<String, Object> requestPayload = Map.of(
                    "amount", amountInPaise,
                    "speed", "normal"
            );

            String responseBody = getClient().post()
                    .uri("/payments/" + paymentId + "/refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestPayload)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String refundId = root.path("id").asText(null);
            if (Objects.isNull(refundId) || refundId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No refund ID returned by Razorpay");
            }
            log.info("Processed Razorpay refund successfully with ID: {}", refundId);
            return refundId;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process Razorpay refund: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to issue payment refund: " + e.getMessage());
        }
    }

    private String calculateHmacSha256(String data, String secret) throws Exception {
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
        return hexString.toString();
    }
}
