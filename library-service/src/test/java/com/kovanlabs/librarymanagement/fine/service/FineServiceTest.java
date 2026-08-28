package com.kovanlabs.librarymanagement.fine.service;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.fine.dto.FineResult;
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

    @Test
    @DisplayName("Should process fine for valid borrow record")
    void testProcessFineForBorrow() {
        Book book = Book.builder().uuid(UUID.randomUUID()).build();
        User user = User.builder().uuid(UUID.randomUUID()).build();
        Borrow borrow = Borrow.builder()
                .uuid(UUID.randomUUID())
                .book(book)
                .user(user)
                .dueDate(LocalDate.now().minusDays(2))
                .build();

        when(fineRepository.findByBookUuidAndUserUuid(book.getUuid(), user.getUuid())).thenReturn(Optional.empty());
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine fine = fineService.processFineForBorrow(borrow);

        assertNotNull(fine);
        assertEquals(BigDecimal.valueOf(10.0), fine.getPendingFineAmount());
    }

    @Test
    @DisplayName("Should calculate total pending fine and check pending fines for user")
    void testPendingFinesAndHasPendingFines() {
        Long userId = 100L;
        UUID userUuid = UUID.randomUUID();
        User user = User.builder().id(userId).uuid(userUuid).build();

        Fine fine1 = Fine.builder().pendingFineAmount(BigDecimal.valueOf(15.0)).status(FineStatus.PENDING).build();
        Fine fine2 = Fine.builder().pendingFineAmount(BigDecimal.valueOf(25.0)).status(FineStatus.PENDING).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING)).thenReturn(List.of(fine1, fine2));

        BigDecimal total = fineService.calculateTotalPendingFineForUser(userId);
        assertEquals(BigDecimal.valueOf(40.0), total);
        assertTrue(fineService.hasPendingFines(userId));
    }

    @Test
    @DisplayName("Should pay fine by fineId")
    void testPayFine() {
        Long fineId = 1L;
        Fine fine = Fine.builder().id(fineId).pendingFineAmount(BigDecimal.valueOf(50.0)).status(FineStatus.PENDING).build();

        when(fineRepository.findById(fineId)).thenReturn(Optional.of(fine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(i -> i.getArgument(0));

        Fine paidFine = fineService.payFine(fineId);
        assertEquals(BigDecimal.ZERO, paidFine.getPendingFineAmount());
        assertEquals(FineStatus.PAID, paidFine.getStatus());
    }


    @Test
    @DisplayName("Should return 0 fine when book is not overdue or borrow/dueDate is null")
    void testCalculateFine_NotOverdueAndNullCheck() {
        Borrow borrow = Borrow.builder()
                .dueDate(LocalDate.now().plusDays(2))
                .build();

        FineResult result = fineService.calculateFine(borrow);
        assertEquals(0, result.daysOverdue());
        assertEquals(0.0, result.fine());

        FineResult nullResult = fineService.calculateFine(null);
        assertEquals(0.0, nullResult.fine());
    }

    @Test
    @DisplayName("Should return null when borrow record is null or missing user/book")
    void testProcessFineForBorrow_NullCases() {
        assertNull(fineService.processFineForBorrow(null));

        Borrow borrowWithoutUser = Borrow.builder().book(Book.builder().uuid(UUID.randomUUID()).build()).build();
        assertNull(fineService.processFineForBorrow(borrowWithoutUser));
    }

    @Test
    @DisplayName("calculateTotalPendingFineForUser should throw exception when user not found")
    void testCalculateTotalPendingFineForUser_UserNotFound_ThrowsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fineService.calculateTotalPendingFineForUser(999L)
        );
        assertTrue(ex.getMessage().contains("User not found with id: 999"));
    }

    @Test
    @DisplayName("getFinesByUserId should throw exception when user not found")
    void testGetFinesByUserId_UserNotFound_ThrowsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fineService.getFinesByUserId(999L)
        );
        assertTrue(ex.getMessage().contains("User not found with id: 999"));
    }

    @Test
    @DisplayName("getPendingFinesByUserId should throw exception when user not found")
    void testGetPendingFinesByUserId_UserNotFound_ThrowsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fineService.getPendingFinesByUserId(999L)
        );
        assertTrue(ex.getMessage().contains("User not found with id: 999"));
    }

    @Test
    @DisplayName("payFine should throw exception when fine record not found")
    void testPayFine_FineNotFound_ThrowsException() {
        when(fineRepository.findById(999L)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                fineService.payFine(999L)
        );
        assertTrue(ex.getMessage().contains("Fine record not found with id: 999"));
    }

    @Test
    @DisplayName("hasPendingFines should return false when user has zero pending fine")
    void testHasPendingFines_NoFines_ReturnsFalse() {
        Long userId = 100L;
        UUID userUuid = UUID.randomUUID();
        User user = User.builder().id(userId).uuid(userUuid).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING)).thenReturn(List.of());

        assertFalse(fineService.hasPendingFines(userId));
    }
}
