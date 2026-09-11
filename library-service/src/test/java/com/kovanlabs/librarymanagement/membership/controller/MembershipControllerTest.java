package com.kovanlabs.librarymanagement.membership.controller;

import com.kovanlabs.librarymanagement.membership.dto.MembershipApplicationResponse;
import com.kovanlabs.librarymanagement.membership.dto.MembershipResponseDto;
import com.kovanlabs.librarymanagement.membership.service.MembershipService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipControllerTest {

    @Mock
    private MembershipService membershipService;

    @Mock
    private Principal principal;

    @InjectMocks
    private MembershipController membershipController;

    private UUID uuid;

    @BeforeEach
    void setUp() {
        uuid = UUID.randomUUID();
        lenient().when(principal.getName()).thenReturn("test@example.com");
    }

    @Test
    void applyForMembership_shouldReturnCreated() {
        MembershipApplicationResponse appResponse = new MembershipApplicationResponse(uuid, 123L, "<html></html>");
        when(membershipService.applyForMembership("test@example.com")).thenReturn(appResponse);

        ResponseEntity<MembershipApplicationResponse> response = membershipController.applyForMembership(principal);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(appResponse, response.getBody());
    }

    @Test
    void signAgreement_shouldReturnOk() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", new byte[]{1, 2, 3});
        MembershipResponseDto responseDto = new MembershipResponseDto(
                uuid, 123L, UUID.randomUUID(), "ACTIVE", LocalDateTime.now(), LocalDate.now(), true, LocalDateTime.now(), "key", "sigBase64", null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(membershipService.signAgreement(eq(uuid), any(), eq("test@example.com"))).thenReturn(responseDto);

        ResponseEntity<MembershipResponseDto> response = membershipController.signAgreement(uuid, file, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(responseDto, response.getBody());
    }

    @Test
    void getMyMembership_shouldReturnOk() {
        MembershipResponseDto responseDto = new MembershipResponseDto(
                uuid, 123L, UUID.randomUUID(), "ACTIVE", LocalDateTime.now(), LocalDate.now(), true, LocalDateTime.now(), "key", "sigBase64", null, LocalDateTime.now(), LocalDateTime.now()
        );
        when(membershipService.getMyMembership("test@example.com")).thenReturn(responseDto);

        ResponseEntity<MembershipResponseDto> response = membershipController.getMyMembership(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(responseDto, response.getBody());
    }

    @Test
    void cancelMembership_shouldReturnOk() {
        MembershipResponseDto responseDto = new MembershipResponseDto(
                uuid, 123L, UUID.randomUUID(), "CANCELLED", null, null, false, null, null, null, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now()
        );
        when(membershipService.cancelMembership("test@example.com")).thenReturn(responseDto);

        ResponseEntity<MembershipResponseDto> response = membershipController.cancelMembership(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(responseDto, response.getBody());
    }

    @Test
    void getAgreementHtml_shouldReturnOk() {
        when(membershipService.getAgreementHtmlByUuid(uuid, "test@example.com")).thenReturn("<html></html>");

        ResponseEntity<String> response = membershipController.getAgreementHtml(uuid, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("<html></html>", response.getBody());
    }

    @Test
    void downloadAgreementPdf_shouldReturnPdfBytes() {
        byte[] bytes = new byte[]{1, 2, 3};
        when(membershipService.downloadAgreementPdf(123L, "test@example.com")).thenReturn(bytes);

        ResponseEntity<byte[]> response = membershipController.downloadAgreementPdf(123L, principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertArrayEquals(bytes, response.getBody());
    }
}
