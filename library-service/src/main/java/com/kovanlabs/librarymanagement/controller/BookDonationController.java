package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.dto.BookDonationRequest;
import com.kovanlabs.librarymanagement.dto.BookDonationResponse;
import com.kovanlabs.librarymanagement.service.BookDonationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/donations")
@RequiredArgsConstructor
public class BookDonationController {

    private final BookDonationService bookDonationService;

    @PostMapping
    public ResponseEntity<BookDonationResponse> createDonationMultipart(
            @ModelAttribute @Valid BookDonationRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Principal principal) {
        String userUuid = Objects.nonNull(principal) ? principal.getName() : null;
        BookDonationResponse response = bookDonationService.createDonation(request, file, userUuid);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/me")
    public ResponseEntity<List<BookDonationResponse>> getMyDonations(Principal principal) {
        if (Objects.isNull(principal)) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        List<BookDonationResponse> response = bookDonationService.getMyDonations(principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{donationUuid}")
    public ResponseEntity<BookDonationResponse> getDonationByUuid(
            @PathVariable("donationUuid") String donationUuid,
            Principal principal) {
        String userUuid = Objects.nonNull(principal) ? principal.getName() : null;
        BookDonationResponse response = bookDonationService.getDonationByUuid(donationUuid, userUuid);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<BookDonationResponse>> getAllDonations() {
        List<BookDonationResponse> response = bookDonationService.getAllDonations();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{donationUuid}")
    public ResponseEntity<BookDonationResponse> updateDonation(
            @PathVariable("donationUuid") String donationUuid,
            @ModelAttribute @Valid BookDonationRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file,
            Principal principal) {
        String userUuid = Objects.nonNull(principal) ? principal.getName() : null;
        BookDonationResponse response = bookDonationService.updateDonation(donationUuid, request, file, userUuid);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{donationUuid}")
    public ResponseEntity<Void> deleteDonation(
            @PathVariable("donationUuid") String donationUuid,
            Principal principal) {
        String userUuid = Objects.nonNull(principal) ? principal.getName() : null;
        bookDonationService.deleteDonation(donationUuid, userUuid);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{donationUuid}/approve")
    public ResponseEntity<BookDonationResponse> approveDonation(
            @PathVariable("donationUuid") String donationUuid,
            Principal principal) {
        String adminUuid = Objects.nonNull(principal) ? principal.getName() : null;
        BookDonationResponse response = bookDonationService.approveDonation(donationUuid, adminUuid);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{donationUuid}/reject")
    public ResponseEntity<BookDonationResponse> rejectDonation(
            @PathVariable("donationUuid") String donationUuid,
            @RequestParam(value = "reason", required = false) String reason,
            @RequestBody(required = false) Map<String, String> body,
            Principal principal) {
        String adminUuid = Objects.nonNull(principal) ? principal.getName() : null;
        String finalReason = (Objects.nonNull(reason) && !reason.isBlank())
                ? reason
                : (Objects.nonNull(body) ? body.get("reason") : null);
        BookDonationResponse response = bookDonationService.rejectDonation(donationUuid, finalReason, adminUuid);
        return ResponseEntity.ok(response);
    }
}
