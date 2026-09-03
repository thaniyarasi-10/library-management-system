package com.kovanlabs.librarymanagement.membership.controller;

import com.kovanlabs.librarymanagement.membership.dto.MembershipApplicationResponse;
import com.kovanlabs.librarymanagement.membership.dto.MembershipResponseDto;
import com.kovanlabs.librarymanagement.membership.service.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/memberships")
@RequiredArgsConstructor
public class MembershipController {

    private final MembershipService membershipService;

    @PostMapping
    public ResponseEntity<MembershipApplicationResponse> applyForMembership(Principal principal) {
        MembershipApplicationResponse response = membershipService.applyForMembership(principal.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{membershipUuid}/sign")
    public ResponseEntity<MembershipResponseDto> signAgreement(
            @PathVariable("membershipUuid") UUID membershipUuid,
            @RequestParam("file") MultipartFile file,
            Principal principal) {
        MembershipResponseDto response = membershipService.signAgreement(membershipUuid, file, principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<MembershipResponseDto> getMyMembership(Principal principal) {
        MembershipResponseDto response = membershipService.getMyMembership(principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/cancel")
    public ResponseEntity<MembershipResponseDto> cancelMembership(Principal principal) {
        MembershipResponseDto response = membershipService.cancelMembership(principal.getName());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{membershipUuid}/agreement")
    public ResponseEntity<String> getAgreementHtml(
            @PathVariable("membershipUuid") UUID membershipUuid,
            Principal principal) {
        String html = membershipService.getAgreementHtmlByUuid(membershipUuid, principal.getName());
        return ResponseEntity.ok(html);
    }

    @GetMapping("/{membershipId}/agreement/pdf")
    public ResponseEntity<byte[]> downloadAgreementPdf(
            @PathVariable("membershipId") Long membershipId,
            Principal principal) {
        byte[] pdfBytes = membershipService.downloadAgreementPdf(membershipId, principal.getName());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(membershipId + "-signed-agreement.pdf")
                .build());
        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }
}
