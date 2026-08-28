package com.kovanlabs.librarymanagement.fine.service;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.fine.dto.FineResult;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.database.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FineServiceTest {

    private FineRepository fineRepository;
    private BookRepository bookRepository;
    private UserRepository userRepository;
    private FineService fineService;

    @BeforeEach
    void setUp() {
        fineRepository = mock(FineRepository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        fineService = new FineService(fineRepository, bookRepository, userRepository);
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
    @DisplayName("Should create new Fine record when no existing fine found for bookUuid and userUuid")
    void testCreateOrUpdateFine_NewRecord() {
        UUID bookUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        BigDecimal amount = BigDecimal.valueOf(25.0);

        when(fineRepository.findByBookUuidAndUserUuid(bookUuid, userUuid)).thenReturn(Optional.empty());
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine fine = fineService.createOrUpdateFine(bookUuid, userUuid, amount);

        assertNotNull(fine);
        assertEquals(bookUuid, fine.getBookUuid());
        assertEquals(userUuid, fine.getUserUuid());
        assertEquals(amount, fine.getPendingFineAmount());
        assertEquals(FineStatus.PENDING, fine.getStatus());
        verify(fineRepository, times(1)).save(any(Fine.class));
    }

    @Test
    @DisplayName("Should update existing Fine record for bookUuid and userUuid")
    void testCreateOrUpdateFine_ExistingRecord() {
        UUID bookUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID fineUuid = UUID.randomUUID();
        BigDecimal oldAmount = BigDecimal.valueOf(15.0);
        BigDecimal newAmount = BigDecimal.valueOf(25.0);

        Fine existingFine = Fine.builder()
                .uuid(fineUuid)
                .id(5L)
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(oldAmount)
                .status(FineStatus.PENDING)
                .build();

        when(fineRepository.findByBookUuidAndUserUuid(bookUuid, userUuid)).thenReturn(Optional.of(existingFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine fine = fineService.createOrUpdateFine(bookUuid, userUuid, newAmount);

        assertEquals(fineUuid, fine.getUuid());
        assertEquals(newAmount, fine.getPendingFineAmount());
        assertEquals(FineStatus.PENDING, fine.getStatus());
        verify(fineRepository, times(1)).save(existingFine);
    }
}
