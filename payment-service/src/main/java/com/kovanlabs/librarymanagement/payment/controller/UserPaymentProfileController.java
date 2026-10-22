package com.kovanlabs.librarymanagement.payment.controller;

import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileRequestDto;
import com.kovanlabs.librarymanagement.payment.dto.UserPaymentProfileResponseDto;
import com.kovanlabs.librarymanagement.payment.service.UserPaymentProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping({"/api/user/payment-profile", "/user/payment-profile"})
@RequiredArgsConstructor
public class UserPaymentProfileController {

    private final UserPaymentProfileService paymentProfileService;

    @GetMapping
    public ResponseEntity<UserPaymentProfileResponseDto> getPaymentProfile(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        UserPaymentProfileResponseDto profile = paymentProfileService.getProfile(principal.getName());
        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<UserPaymentProfileResponseDto> saveOrUpdatePaymentProfile(
            @Valid @RequestBody UserPaymentProfileRequestDto request,
            Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        UserPaymentProfileResponseDto profile = paymentProfileService.saveOrUpdateProfile(request, principal.getName());
        return ResponseEntity.ok(profile);
    }

    @DeleteMapping
    public ResponseEntity<Void> deletePaymentProfile(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return ResponseEntity.status(401).build();
        }
        paymentProfileService.deleteProfile(principal.getName());
        return ResponseEntity.noContent().build();
    }
}
