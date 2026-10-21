package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping({"/api/webhook/razorpay", "/webhook/razorpay"})
@RequiredArgsConstructor
public class RazorpayWebhookController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Map<String, String>> handleRazorpayWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
        log.info("Received Razorpay webhook request at /api/webhook/razorpay");
        paymentService.handleWebhook(rawPayload, signatureHeader);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Webhook processed successfully"
        ));
    }
}
