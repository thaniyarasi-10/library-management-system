package com.kovanlabs.librarymanagement.controller;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.dto.FineResponseDto;
import com.kovanlabs.librarymanagement.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FineControllerTest {

    @Mock
    private FineService fineService;

    @Mock
    private Principal principal;

    @InjectMocks
    private FineController fineController;

    private String fineUuid;
    private String bookUuid;
    private String userUuid;
    private Long fineId;
    private Long userId;

    @BeforeEach
    void setUp() {
        fineUuid = UUID.randomUUID().toString();
        bookUuid = UUID.randomUUID().toString();
        userUuid = UUID.randomUUID().toString();
        fineId = 1L;
        userId = 20L;
    }

    @Test
    @DisplayName("getAllFines should return all fines DTO list")
    void testGetAllFines() {
        FineResponseDto dto = FineResponseDto.builder()
                .id(fineId)
                .amount(BigDecimal.TEN)
                .status(FineStatus.PENDING)
                .build();
        when(fineService.getAllFinesDto()).thenReturn(List.of(dto));

        ResponseEntity<List<FineResponseDto>> response = fineController.getAllFines();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(fineService).getAllFinesDto();
    }

    @Test
    @DisplayName("getMyFines with principal should return user's fines")
    void testGetMyFines_withPrincipal() {
        when(principal.getName()).thenReturn("test@example.com");
        FineResponseDto dto = FineResponseDto.builder()
                .id(fineId)
                .amount(BigDecimal.TEN)
                .status(FineStatus.PENDING)
                .build();
        when(fineService.getFinesDtoByUserEmail("test@example.com")).thenReturn(List.of(dto));

        ResponseEntity<List<FineResponseDto>> response = fineController.getMyFines(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        verify(fineService).getFinesDtoByUserEmail("test@example.com");
    }

    @Test
    @DisplayName("getMyFines with null principal should return empty list")
    void testGetMyFines_withNullPrincipal() {
        ResponseEntity<List<FineResponseDto>> response = fineController.getMyFines(null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        verifyNoInteractions(fineService);
    }

    @Test
    @DisplayName("Should pay fine by ID")
    void testPayFine() {
        Fine fine = Fine.builder()
                .uuid(fineUuid)
                .id(fineId)
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PAID)
                .build();
        FineResponseDto dto = FineResponseDto.builder()
                .id(fineId)
                .pendingFineAmount(BigDecimal.ZERO)
                .amount(BigDecimal.ZERO)
                .status(FineStatus.PAID)
                .build();

        when(fineService.payFine(fineId)).thenReturn(fine);
        when(fineService.mapToDtoWithDetails(fine)).thenReturn(dto);

        ResponseEntity<FineResponseDto> response = fineController.payFine(fineId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(FineStatus.PAID, response.getBody().status());
        assertEquals(BigDecimal.ZERO, response.getBody().pendingFineAmount());
        verify(fineService, times(1)).payFine(fineId);
    }

    @Test
    @DisplayName("Should get user total pending fine")
    void testGetUserTotalPendingFine() {
        BigDecimal expectedTotal = BigDecimal.valueOf(50.0);

        when(fineService.calculateTotalPendingFineForUser(userId)).thenReturn(expectedTotal);

        ResponseEntity<BigDecimal> response = fineController.getUserTotalPendingFine(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedTotal, response.getBody());
        verify(fineService, times(1)).calculateTotalPendingFineForUser(userId);
    }

    @Test
    @DisplayName("Should get fines and pending fines by user ID")
    void testGetFinesByUserId() {
        when(fineService.getFinesDtoByUserId(userId)).thenReturn(List.of());
        when(fineService.getPendingFinesByUserId(userId)).thenReturn(List.of());

        ResponseEntity<List<FineResponseDto>> responseAll = fineController.getFinesByUserId(userId);
        ResponseEntity<List<Fine>> responsePending = fineController.getPendingFinesByUserId(userId);

        assertEquals(HttpStatus.OK, responseAll.getStatusCode());
        assertEquals(HttpStatus.OK, responsePending.getStatusCode());
    }
}
