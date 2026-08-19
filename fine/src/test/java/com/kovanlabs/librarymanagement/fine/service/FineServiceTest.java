package com.kovanlabs.librarymanagement.fine.service;

import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import com.kovanlabs.librarymanagement.fine.dto.FineResult;
import com.kovanlabs.librarymanagement.fine.entity.Fine;
import com.kovanlabs.librarymanagement.fine.enums.FineStatus;
import com.kovanlabs.librarymanagement.fine.repository.FineRepository;
import com.kovanlabs.librarymanagement.user.entity.Users;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FineServiceTest {

    private FineRepository fineRepository;
    private FineService fineService;

    @BeforeEach
    void setUp() {
        fineRepository = mock(FineRepository.class);
        fineService = new FineService(fineRepository);
    }

    @Test
    @DisplayName("Should calculate fine at ₹5 per overdue day per book")
    void testCalculateFine() {
        Borrow borrow = Borrow.builder()
                .dueDate(LocalDate.now().minusDays(4))
                .build();

        FineResult result = fineService.calculateFine(borrow);

        assertEquals(4, result.daysOverdue());
        assertEquals(20.0, result.fine());
    }

    @Test
    @DisplayName("Should return 0 fine when book is not overdue")
    void testCalculateFine_NotOverdue() {
        Borrow borrow = Borrow.builder()
                .dueDate(LocalDate.now().plusDays(2))
                .build();

        FineResult result = fineService.calculateFine(borrow);

        assertEquals(0, result.daysOverdue());
        assertEquals(0.0, result.fine());
    }

    @Test
    @DisplayName("Should create new Fine record when no existing fine found for book_id and user_id")
    void testCreateOrUpdateFine_NewRecord() {
        Long bookId = 101L;
        Long userId = 1L;
        BigDecimal amount = BigDecimal.valueOf(25.0);

        when(fineRepository.findByBookIdAndUserId(bookId, userId)).thenReturn(Optional.empty());
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine fine = fineService.createOrUpdateFine(bookId, userId, amount);

        assertNotNull(fine);
        assertEquals(bookId, fine.getBookId());
        assertEquals(userId, fine.getUserId());
        assertEquals(amount, fine.getPendingFineAmount());
        assertEquals(FineStatus.PENDING, fine.getStatus());
        verify(fineRepository, times(1)).save(any(Fine.class));
    }

    @Test
    @DisplayName("Should update existing Fine record for book_id and user_id")
    void testCreateOrUpdateFine_ExistingRecord() {
        Long bookId = 101L;
        Long userId = 1L;
        BigDecimal oldAmount = BigDecimal.valueOf(15.0);
        BigDecimal newAmount = BigDecimal.valueOf(25.0);

        Fine existingFine = Fine.builder()
                .id(5L)
                .bookId(bookId)
                .userId(userId)
                .pendingFineAmount(oldAmount)
                .status(FineStatus.PENDING)
                .build();

        when(fineRepository.findByBookIdAndUserId(bookId, userId)).thenReturn(Optional.of(existingFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine fine = fineService.createOrUpdateFine(bookId, userId, newAmount);

        assertEquals(5L, fine.getId());
        assertEquals(newAmount, fine.getPendingFineAmount());
        assertEquals(FineStatus.PENDING, fine.getStatus());
        verify(fineRepository, times(1)).save(existingFine);
    }

    @Test
    @DisplayName("Should calculate user total pending fine by summing all PENDING fine records")
    void testCalculateTotalPendingFineForUser() {
        Long userId = 1L;

        Fine fine1 = Fine.builder().id(1L).userId(userId).pendingFineAmount(BigDecimal.valueOf(15.0)).status(FineStatus.PENDING).build();
        Fine fine2 = Fine.builder().id(2L).userId(userId).pendingFineAmount(BigDecimal.valueOf(25.0)).status(FineStatus.PENDING).build();

        when(fineRepository.findByUserIdAndStatus(userId, FineStatus.PENDING)).thenReturn(List.of(fine1, fine2));

        BigDecimal totalPending = fineService.calculateTotalPendingFineForUser(userId);

        assertEquals(BigDecimal.valueOf(40.0), totalPending);
    }

    @Test
    @DisplayName("Should process payment flow: set pendingFineAmount = 0 and status = PAID")
    void testPayFine() {
        Long fineId = 10L;
        Fine fine = Fine.builder()
                .id(fineId)
                .bookId(101L)
                .userId(1L)
                .pendingFineAmount(BigDecimal.valueOf(30.0))
                .status(FineStatus.PENDING)
                .build();

        when(fineRepository.findById(fineId)).thenReturn(Optional.of(fine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine paidFine = fineService.payFine(fineId);

        assertEquals(BigDecimal.ZERO, paidFine.getPendingFineAmount());
        assertEquals(FineStatus.PAID, paidFine.getStatus());
        verify(fineRepository, times(1)).save(fine);
    }
}
