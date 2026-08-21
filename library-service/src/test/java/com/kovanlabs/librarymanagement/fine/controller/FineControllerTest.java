package com.kovanlabs.librarymanagement.fine.controller;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.fine.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FineControllerTest {

    private FineService fineService;
    private FineController fineController;
    private UUID fineUuid;
    private UUID bookUuid;
    private UUID userUuid;
    private Long fineId;
    private Long bookId;
    private Long userId;

    @BeforeEach
    void setUp() {
        fineService = mock(FineService.class);
        fineController = new FineController(fineService);
        fineUuid = UUID.randomUUID();
        bookUuid = UUID.randomUUID();
        userUuid = UUID.randomUUID();
        fineId = 1L;
        bookId = 10L;
        userId = 20L;
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

        when(fineService.payFine(fineId)).thenReturn(fine);

        ResponseEntity<Fine> response = fineController.payFine(fineId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(FineStatus.PAID, response.getBody().getStatus());
        assertEquals(BigDecimal.ZERO, response.getBody().getPendingFineAmount());
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
}
