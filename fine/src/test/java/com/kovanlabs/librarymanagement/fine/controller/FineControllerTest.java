package com.kovanlabs.librarymanagement.fine.controller;

import com.kovanlabs.librarymanagement.fine.entity.Fine;
import com.kovanlabs.librarymanagement.fine.enums.FineStatus;
import com.kovanlabs.librarymanagement.fine.service.FineService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FineControllerTest {

    private FineService fineService;
    private FineController fineController;

    @BeforeEach
    void setUp() {
        fineService = mock(FineService.class);
        fineController = new FineController(fineService);
    }

    @Test
    @DisplayName("Should pay fine by ID")
    void testPayFine() {
        Long fineId = 1L;
        Fine fine = Fine.builder()
                .id(fineId)
                .bookId(101L)
                .userId(2L)
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
        Long userId = 2L;
        BigDecimal expectedTotal = BigDecimal.valueOf(50.0);

        when(fineService.calculateTotalPendingFineForUser(userId)).thenReturn(expectedTotal);

        ResponseEntity<BigDecimal> response = fineController.getUserTotalPendingFine(userId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expectedTotal, response.getBody());
        verify(fineService, times(1)).calculateTotalPendingFineForUser(userId);
    }
}
