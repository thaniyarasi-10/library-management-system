package com.kovanlabs.librarymanagement.payment.controller;

import com.kovanlabs.librarymanagement.payment.dto.MembershipCancellationRefundResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentOrderResponse;
import com.kovanlabs.librarymanagement.payment.dto.PaymentResponseDto;
import com.kovanlabs.librarymanagement.payment.dto.VerifyPaymentRequest;
import com.kovanlabs.librarymanagement.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping({"/api/payments", "/payments"})
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/membership/order")
    public ResponseEntity<PaymentOrderResponse> createMembershipOrder(Principal principal) {
        PaymentOrderResponse response = paymentService.createMembershipOrder(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/fine/{fineId}/order")
    public ResponseEntity<PaymentOrderResponse> createFineOrder(
            @PathVariable("fineId") Long fineId,
            Principal principal) {
        PaymentOrderResponse response = paymentService.createFineOrder(fineId, principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentResponseDto> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            Principal principal) {
        PaymentResponseDto response = paymentService.verifyPayment(request, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/membership/cancel-with-refund")
    public ResponseEntity<MembershipCancellationRefundResponse> cancelMembershipWithRefund(Principal principal) {
        MembershipCancellationRefundResponse response = paymentService.cancelMembershipWithRefund(principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-payments")
    public ResponseEntity<List<PaymentResponseDto>> getUserPayments(Principal principal) {
        List<PaymentResponseDto> payments = paymentService.getUserPayments(principal.getName());
        return ResponseEntity.ok(payments);
    }

    @GetMapping("/{paymentId}/invoice")
    public ResponseEntity<byte[]> downloadInvoice(
            @PathVariable("paymentId") String paymentId,
            Principal principal) {
        byte[] pdfBytes = paymentService.downloadInvoice(paymentId, principal.getName());
        String rawUsername = principal.getName();
        if (rawUsername != null && rawUsername.contains("@")) {
            rawUsername = rawUsername.substring(0, rawUsername.indexOf("@"));
        }
        String cleanUsername = (rawUsername != null && !rawUsername.isBlank())
                ? rawUsername.replaceAll("[^a-zA-Z0-9_-]", "_")
                : "user";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Padips-Document-" + cleanUsername + ".pdf\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                .body(pdfBytes);
    }
}
