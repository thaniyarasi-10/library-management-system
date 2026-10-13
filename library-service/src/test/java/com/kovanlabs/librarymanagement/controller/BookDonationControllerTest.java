package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.database.enums.DonationStatus;
import com.kovanlabs.librarymanagement.dto.BookDonationRequest;
import com.kovanlabs.librarymanagement.dto.BookDonationResponse;
import com.kovanlabs.librarymanagement.service.BookDonationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookDonationControllerTest {

    @Mock
    private BookDonationService bookDonationService;

    @Mock
    private Principal principal;

    @InjectMocks
    private BookDonationController controller;

    private Long donationId;
    private BookDonationResponse sampleResponse;

    @BeforeEach
    void setUp() {
        donationId = 1L;
        sampleResponse = BookDonationResponse.builder()
                .id(donationId)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .isbn("9780134494166")
                .donatedBookCount(2)
                .status(DonationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("createDonationMultipart - with file and principal should return 201 Created")
    void createDonationMultipart_withFile_shouldReturnCreated() {
        BookDonationRequest request = new BookDonationRequest("Clean Architecture", "Robert C. Martin", "9780134494166", 2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", "image-bytes".getBytes()
        );
        when(principal.getName()).thenReturn("donor@example.com");
        when(bookDonationService.createDonation(eq(request), eq(file), eq("donor@example.com"))).thenReturn(sampleResponse);

        ResponseEntity<BookDonationResponse> result = controller.createDonationMultipart(request, file, principal);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(donationId, result.getBody().id());
        verify(bookDonationService).createDonation(request, file, "donor@example.com");
    }

    @Test
    @DisplayName("createDonationMultipart - with null file and null principal should return 201 Created")
    void createDonationMultipart_withNullFileAndNullPrincipal_shouldReturnCreated() {
        BookDonationRequest request = new BookDonationRequest("Clean Architecture", "Robert C. Martin", "9780134494166", 2);
        when(bookDonationService.createDonation(eq(request), isNull(), isNull())).thenReturn(sampleResponse);

        ResponseEntity<BookDonationResponse> result = controller.createDonationMultipart(request, null, null);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertNotNull(result.getBody());
        verify(bookDonationService).createDonation(request, null, null);
    }

    @Test
    @DisplayName("getMyDonations - with valid principal should return user's donations")
    void getMyDonations_withPrincipal_shouldReturnList() {
        when(principal.getName()).thenReturn("donor@example.com");
        when(bookDonationService.getMyDonations("donor@example.com")).thenReturn(List.of(sampleResponse));

        ResponseEntity<List<BookDonationResponse>> result = controller.getMyDonations(principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        assertEquals(donationId, result.getBody().get(0).id());
        verify(bookDonationService).getMyDonations("donor@example.com");
    }

    @Test
    @DisplayName("getMyDonations - with null principal should return empty list without calling service")
    void getMyDonations_nullPrincipal_shouldReturnEmptyList() {
        ResponseEntity<List<BookDonationResponse>> result = controller.getMyDonations(null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertTrue(result.getBody().isEmpty());
        verifyNoInteractions(bookDonationService);
    }

    @Test
    @DisplayName("getDonationByUuid - with principal should return donation response")
    void getDonationByUuid_withPrincipal_shouldReturnDonation() {
        when(principal.getName()).thenReturn("donor@example.com");
        when(bookDonationService.getDonationByUuid("1", "donor@example.com")).thenReturn(sampleResponse);

        ResponseEntity<BookDonationResponse> result = controller.getDonationByUuid("1", principal);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(donationId, result.getBody().id());
        verify(bookDonationService).getDonationByUuid("1", "donor@example.com");
    }

    @Test
    @DisplayName("getDonationByUuid - with null principal should pass null identifier to service")
    void getDonationByUuid_nullPrincipal_shouldPassNull() {
        when(bookDonationService.getDonationByUuid("1", null)).thenReturn(sampleResponse);

        ResponseEntity<BookDonationResponse> result = controller.getDonationByUuid("1", null);

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        verify(bookDonationService).getDonationByUuid("1", null);
    }

    @Test
    @DisplayName("getAllDonations - should return list of all donations")
    void getAllDonations_shouldReturnAll() {
        when(bookDonationService.getAllDonations()).thenReturn(List.of(sampleResponse));

        ResponseEntity<List<BookDonationResponse>> result = controller.getAllDonations();

        assertEquals(HttpStatus.OK, result.getStatusCode());
        assertNotNull(result.getBody());
        assertEquals(1, result.getBody().size());
        verify(bookDonationService).getAllDonations();
    }

    @Test
    @DisplayName("approveDonation - should call approveDonation with principal and with null principal")
    void approveDonation_shouldReturnOk() {
        when(principal.getName()).thenReturn("admin@example.com");
        BookDonationResponse approved = BookDonationResponse.builder()
                .id(donationId)
                .status(DonationStatus.APPROVED)
                .build();
        when(bookDonationService.approveDonation("1", "admin@example.com")).thenReturn(approved);
        when(bookDonationService.approveDonation("1", null)).thenReturn(approved);

        ResponseEntity<BookDonationResponse> resultWithPrincipal = controller.approveDonation("1", principal);
        assertEquals(HttpStatus.OK, resultWithPrincipal.getStatusCode());
        assertEquals(DonationStatus.APPROVED, resultWithPrincipal.getBody().status());

        ResponseEntity<BookDonationResponse> resultNullPrincipal = controller.approveDonation("1", null);
        assertEquals(HttpStatus.OK, resultNullPrincipal.getStatusCode());
    }

    @Test
    @DisplayName("rejectDonation - should call rejectDonation with reason parameter, request body, and null combinations")
    void rejectDonation_shouldReturnOk() {
        when(principal.getName()).thenReturn("admin@example.com");
        BookDonationResponse rejected = BookDonationResponse.builder()
                .id(donationId)
                .status(DonationStatus.REJECTED)
                .build();
        when(bookDonationService.rejectDonation("1", "Duplicate", "admin@example.com")).thenReturn(rejected);
        when(bookDonationService.rejectDonation("1", "BodyReason", "admin@example.com")).thenReturn(rejected);
        when(bookDonationService.rejectDonation("1", null, "admin@example.com")).thenReturn(rejected);
        when(bookDonationService.rejectDonation("1", null, null)).thenReturn(rejected);

        // 1. Valid param reason
        ResponseEntity<BookDonationResponse> resultWithParam = controller.rejectDonation("1", "Duplicate", null, principal);
        assertEquals(HttpStatus.OK, resultWithParam.getStatusCode());
        assertEquals(DonationStatus.REJECTED, resultWithParam.getBody().status());

        // 2. Blank param reason -> fallback to non-null body reason
        ResponseEntity<BookDonationResponse> resultWithBlankParamAndBody = controller.rejectDonation("1", "   ", Map.of("reason", "BodyReason"), principal);
        assertEquals(HttpStatus.OK, resultWithBlankParamAndBody.getStatusCode());
        assertEquals(DonationStatus.REJECTED, resultWithBlankParamAndBody.getBody().status());

        // 3. Null param reason -> fallback to non-null body reason
        ResponseEntity<BookDonationResponse> resultWithBody = controller.rejectDonation("1", null, Map.of("reason", "BodyReason"), principal);
        assertEquals(HttpStatus.OK, resultWithBody.getStatusCode());
        assertEquals(DonationStatus.REJECTED, resultWithBody.getBody().status());

        // 4. Null param reason and null body
        ResponseEntity<BookDonationResponse> resultNullBody = controller.rejectDonation("1", null, null, principal);
        assertEquals(HttpStatus.OK, resultNullBody.getStatusCode());

        // 5. Null param reason, null body, null principal
        ResponseEntity<BookDonationResponse> resultNullPrincipal = controller.rejectDonation("1", null, null, null);
        assertEquals(HttpStatus.OK, resultNullPrincipal.getStatusCode());
    }

    @Test
    @DisplayName("updateDonation - should call service with principal and with null principal")
    void updateDonation_shouldReturnOk() {
        BookDonationRequest request = new BookDonationRequest("Clean Architecture", "Robert C. Martin", "9780134494166", 3);
        MockMultipartFile file = new MockMultipartFile("file", "cover.png", "image/png", "bytes".getBytes());
        when(principal.getName()).thenReturn("donor@example.com");
        when(bookDonationService.updateDonation("1", request, file, "donor@example.com")).thenReturn(sampleResponse);
        when(bookDonationService.updateDonation("1", request, null, null)).thenReturn(sampleResponse);

        ResponseEntity<BookDonationResponse> resultWithPrincipal = controller.updateDonation("1", request, file, principal);
        assertEquals(HttpStatus.OK, resultWithPrincipal.getStatusCode());
        assertEquals(donationId, resultWithPrincipal.getBody().id());

        ResponseEntity<BookDonationResponse> resultNullPrincipal = controller.updateDonation("1", request, null, null);
        assertEquals(HttpStatus.OK, resultNullPrincipal.getStatusCode());
    }

    @Test
    @DisplayName("deleteDonation - should call service with principal and with null principal")
    void deleteDonation_shouldReturnNoContent() {
        when(principal.getName()).thenReturn("donor@example.com");

        ResponseEntity<Void> resultWithPrincipal = controller.deleteDonation("1", principal);
        assertEquals(HttpStatus.NO_CONTENT, resultWithPrincipal.getStatusCode());
        verify(bookDonationService).deleteDonation("1", "donor@example.com");

        ResponseEntity<Void> resultNullPrincipal = controller.deleteDonation("1", null);
        assertEquals(HttpStatus.NO_CONTENT, resultNullPrincipal.getStatusCode());
        verify(bookDonationService).deleteDonation("1", null);
    }
}
