package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.dto.MembershipApplicationResponse;
import com.kovanlabs.librarymanagement.dto.MembershipResponseDto;
import com.kovanlabs.librarymanagement.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipControllerTest {

    @Mock
    private MembershipService membershipService;

    @Mock
    private Principal principal;

    @InjectMocks
    private MembershipController membershipController;

    private final String email = "user@example.com";
    private final UUID membershipUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(principal.getName()).thenReturn(email);
    }

    private MembershipResponseDto createSampleDto(String status) {
        return new MembershipResponseDto(
                membershipUuid, 123456L, UUID.randomUUID(), status,
                LocalDateTime.now(), LocalDate.now().plusYears(1), true,
                LocalDateTime.now(), "key.pdf", "sigBase64",
                null, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void testApplyForMembership() {
        MembershipApplicationResponse response = new MembershipApplicationResponse(membershipUuid, null, "<html></html>");
        when(membershipService.applyForMembership(email)).thenReturn(response);

        ResponseEntity<MembershipApplicationResponse> result = membershipController.applyForMembership(principal);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(membershipService).applyForMembership(email);
    }

    @Test
    void testSignAgreement() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", new byte[]{1, 2, 3});
        MembershipResponseDto response = createSampleDto("ACTIVE");
        when(membershipService.signAgreement(eq(membershipUuid), any(), eq(email))).thenReturn(response);

        ResponseEntity<MembershipResponseDto> result = membershipController.signAgreement(membershipUuid, file, principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(membershipService).signAgreement(membershipUuid, file, email);
    }

    @Test
    void testGetMyMembership() {
        MembershipResponseDto response = createSampleDto("ACTIVE");
        when(membershipService.getMyMembership(email)).thenReturn(response);

        ResponseEntity<MembershipResponseDto> result = membershipController.getMyMembership(principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(membershipService).getMyMembership(email);
    }

    @Test
    void testCancelMembership() {
        MembershipResponseDto response = createSampleDto("CANCELLED");
        when(membershipService.cancelMembership(email)).thenReturn(response);

        ResponseEntity<MembershipResponseDto> result = membershipController.cancelMembership(principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(response, result.getBody());
        verify(membershipService).cancelMembership(email);
    }

    @Test
    void testGetAgreementHtml() {
        String html = "<html><body>Agreement</body></html>";
        when(membershipService.getAgreementHtmlByUuid(membershipUuid, email)).thenReturn(html);

        ResponseEntity<String> result = membershipController.getAgreementHtml(membershipUuid, principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertEquals(html, result.getBody());
        verify(membershipService).getAgreementHtmlByUuid(membershipUuid, email);
    }

    @Test
    void testDownloadAgreementPdf() {
        Long membershipId = 123456L;
        byte[] pdfBytes = "PDF-BYTES".getBytes();
        when(membershipService.downloadAgreementPdf(membershipId, email)).thenReturn(pdfBytes);

        ResponseEntity<byte[]> result = membershipController.downloadAgreementPdf(membershipId, principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertArrayEquals(pdfBytes, result.getBody());
        assertEquals(MediaType.APPLICATION_PDF, result.getHeaders().getContentType());
        assertTrue(result.getHeaders().getContentDisposition().toString().contains("123456-signed-agreement.pdf"));
        verify(membershipService).downloadAgreementPdf(membershipId, email);
    }
}
