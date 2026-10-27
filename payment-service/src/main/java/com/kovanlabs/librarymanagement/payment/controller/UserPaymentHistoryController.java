package com.kovanlabs.librarymanagement.payment.controller;

import com.kovanlabs.librarymanagement.database.enums.PaymentStatus;
import com.kovanlabs.librarymanagement.database.enums.PaymentType;
import com.kovanlabs.librarymanagement.payment.dto.PaymentHistoryResponseDto;
import com.kovanlabs.librarymanagement.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

@RestController
@RequestMapping({"/api/user/me/payment-history", "/user/me/payment-history"})
@RequiredArgsConstructor
public class UserPaymentHistoryController {

    private final PaymentService paymentService;

    @GetMapping
    public ResponseEntity<PaymentHistoryResponseDto> getPaymentHistory(
            @RequestParam(value = "paymentType", required = false) PaymentType paymentType,
            @RequestParam(value = "status", required = false) PaymentStatus status,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            Principal principal
    ) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User identity is required");
        }
        PaymentHistoryResponseDto response = paymentService.getPaymentHistory(
                principal.getName(),
                paymentType,
                status,
                search,
                page,
                size
        );
        return ResponseEntity.ok(response);
    }
}
