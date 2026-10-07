package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.FineRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.dto.FineResponseDto;
import com.kovanlabs.librarymanagement.dto.FineResult;
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
    private com.kovanlabs.librarymanagement.database.repository.UserProviderRepository userProviderRepository;
    private BorrowRepository borrowRepository;
    private FineService fineService;

    @BeforeEach
    void setUp() {
        fineRepository = mock(FineRepository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        userProviderRepository = mock(com.kovanlabs.librarymanagement.database.repository.UserProviderRepository.class);
        borrowRepository = mock(BorrowRepository.class);
        fineService = new FineService(fineRepository, bookRepository, userRepository, userProviderRepository, borrowRepository);
    }

    @Test
    @DisplayName("Should calculate fine at ₹5 per overdue day per book with returnedDate vs now")
    void testCalculateFine() {
        // Returned late
        Borrow borrowReturnedLate = Borrow.builder()
                .dueDate(LocalDate.of(2026, 1, 1))
                .returnedDate(LocalDate.of(2026, 1, 5))
                .build();

        FineResult resLate = fineService.calculateFine(borrowReturnedLate);
        assertEquals(4, resLate.daysOverdue());
        assertEquals(20.0, resLate.fine());

        // Currently overdue (no returned date)
        Borrow borrowCurrent = Borrow.builder()
                .dueDate(LocalDate.now().minusDays(4))
                .build();

        FineResult result = fineService.calculateFine(borrowCurrent);
        assertEquals(4, result.daysOverdue());
        assertEquals(20.0, result.fine());

        // Null borrow or null dueDate
        assertEquals(0.0, fineService.calculateFine(null).fine());
        assertEquals(0.0, fineService.calculateFine(Borrow.builder().dueDate(null).build()).fine());
    }

    @Test
    @DisplayName("Should create new Fine record when no existing fine found for bookUuid and userUuid")
    void testCreateOrUpdateFine_NewRecord() {
        String bookUuid = UUID.randomUUID().toString();
        String userUuid = UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(25.0);

        when(fineRepository.findTopByBookUuidAndUserUuidOrderByIdDesc(bookUuid, userUuid)).thenReturn(Optional.empty());
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
    @DisplayName("Should update existing Fine record for bookUuid and userUuid only if PENDING")
    void testCreateOrUpdateFine_ExistingRecord() {
        String bookUuid = UUID.randomUUID().toString();
        String userUuid = UUID.randomUUID().toString();
        String fineUuid = UUID.randomUUID().toString();
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

        when(fineRepository.findTopByBookUuidAndUserUuidOrderByIdDesc(bookUuid, userUuid)).thenReturn(Optional.of(existingFine));
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine fine = fineService.createOrUpdateFine(bookUuid, userUuid, newAmount);

        assertEquals(fineUuid, fine.getUuid());
        assertEquals(newAmount, fine.getPendingFineAmount());
        assertEquals(FineStatus.PENDING, fine.getStatus());

        // Existing fine with status PAID should not update pendingFineAmount
        Fine paidFine = Fine.builder()
                .uuid(UUID.randomUUID().toString())
                .id(6L)
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PAID)
                .build();
        when(fineRepository.findTopByBookUuidAndUserUuidOrderByIdDesc(bookUuid, userUuid)).thenReturn(Optional.of(paidFine));
        Fine resultPaid = fineService.createOrUpdateFine(bookUuid, userUuid, newAmount);
        assertEquals(BigDecimal.ZERO, resultPaid.getPendingFineAmount());
    }

    @Test
    @DisplayName("Should process fine for valid borrow record")
    void testProcessFineForBorrow() {
        Book book = Book.builder().uuid(UUID.randomUUID().toString()).build();
        User user = User.builder().uuid(UUID.randomUUID().toString()).build();
        Borrow borrow = Borrow.builder()
                .uuid(UUID.randomUUID().toString())
                .book(book)
                .user(user)
                .dueDate(LocalDate.now().minusDays(2))
                .build();

        when(fineRepository.findTopByBookUuidAndUserUuidOrderByIdDesc(book.getUuid(), user.getUuid())).thenReturn(Optional.empty());
        when(fineRepository.save(any(Fine.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Fine fine = fineService.processFineForBorrow(borrow);

        assertNotNull(fine);
        assertEquals(BigDecimal.valueOf(10.0), fine.getPendingFineAmount());
    }

    @Test
    @DisplayName("Should calculate total pending fine and check pending fines for user")
    void testPendingFinesAndHasPendingFines() {
        Long userId = 100L;
        String userUuid = UUID.randomUUID().toString();
        User user = User.builder().id(userId).uuid(userUuid).build();

        Fine fine1 = Fine.builder().pendingFineAmount(BigDecimal.valueOf(15.0)).status(FineStatus.PENDING).build();
        Fine fine2 = Fine.builder().pendingFineAmount(BigDecimal.valueOf(25.0)).status(FineStatus.PENDING).build();
        Fine fine3WithNullAmount = Fine.builder().pendingFineAmount(null).status(FineStatus.PENDING).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING)).thenReturn(List.of(fine1, fine2, fine3WithNullAmount));

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
        assertEquals(BigDecimal.valueOf(50.0), paidFine.getPendingFineAmount());
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

        Borrow borrowWithoutUser = Borrow.builder().uuid("b123").book(Book.builder().uuid(UUID.randomUUID().toString()).build()).user(null).build();
        assertNull(fineService.processFineForBorrow(borrowWithoutUser));

        Borrow borrowWithoutBook = Borrow.builder().uuid("b124").book(null).user(User.builder().uuid(UUID.randomUUID().toString()).build()).build();
        assertNull(fineService.processFineForBorrow(borrowWithoutBook));
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
        String userUuid = UUID.randomUUID().toString();
        User user = User.builder().id(userId).uuid(userUuid).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING)).thenReturn(List.of());

        assertFalse(fineService.hasPendingFines(userId));
    }

    @Test
    @DisplayName("getAllFinesDto should return mapped DTOs")
    void testGetAllFinesDto() {
        String bookUuid = UUID.randomUUID().toString();
        String userUuid = UUID.randomUUID().toString();
        Fine fine = Fine.builder()
                .id(1L)
                .uuid(UUID.randomUUID().toString())
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.TEN)
                .status(FineStatus.PENDING)
                .build();
        Book book = Book.builder().id(10L).uuid(bookUuid).title("Test Book").author("Test Author").build();
        User user = User.builder().id(20L).uuid(userUuid).name("Test User").email("test@example.com").build();

        when(fineRepository.findAllByOrderByIdDesc()).thenReturn(List.of(fine));
        when(bookRepository.findByUuid(bookUuid)).thenReturn(Optional.of(book));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));

        var dtos = fineService.getAllFinesDto();
        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals("Test Book", dtos.get(0).bookTitle());
        assertEquals("Test User", dtos.get(0).userName());
    }

    @Test
    @DisplayName("getFinesDtoByUserId should return mapped DTOs for user")
    void testGetFinesDtoByUserId() {
        Long userId = 20L;
        String userUuid = UUID.randomUUID().toString();
        User user = User.builder().id(userId).uuid(userUuid).name("Test User").email("test@example.com").build();
        Fine fine = Fine.builder()
                .id(1L)
                .bookUuid(UUID.randomUUID().toString())
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.TEN)
                .status(FineStatus.PENDING)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuidOrderByIdDesc(userUuid)).thenReturn(List.of(fine));

        var dtos = fineService.getFinesDtoByUserId(userId);
        assertNotNull(dtos);
        assertEquals(1, dtos.size());
    }

    @Test
    @DisplayName("getFinesDtoByUserId throws when user not found")
    void testGetFinesDtoByUserId_notFound_throws() {
        when(userRepository.findById(123L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> fineService.getFinesDtoByUserId(123L));
    }

    @Test
    @DisplayName("getFinesDtoByUserEmail should handle null, provider match, or email match")
    void testGetFinesDtoByUserEmail() {
        // null email
        assertTrue(fineService.getFinesDtoByUserEmail(null).isEmpty());

        // provider match with user id null
        String providerEmail = "provider-only@example.com";
        String userUuid = UUID.randomUUID().toString();
        User userNullId = User.builder().id(null).uuid(userUuid).build();
        com.kovanlabs.librarymanagement.database.entity.UserProvider upNull = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(userUuid)
                .build();
        when(userProviderRepository.findByProviderId(providerEmail)).thenReturn(Optional.of(upNull));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(userNullId));
        assertTrue(fineService.getFinesDtoByUserEmail(providerEmail).isEmpty());

        // provider match with valid user
        String email = "member@example.com";
        User user = User.builder().id(20L).uuid(userUuid).build();
        com.kovanlabs.librarymanagement.database.entity.UserProvider up = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(userUuid)
                .build();
        when(userProviderRepository.findByProviderId(email)).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(userRepository.findById(20L)).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuidOrderByIdDesc(userUuid)).thenReturn(List.of());

        var result = fineService.getFinesDtoByUserEmail(email);
        assertNotNull(result);

        // email match fallback
        when(userProviderRepository.findByProviderId("direct@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("direct@example.com")).thenReturn(Optional.of(user));
        var result2 = fineService.getFinesDtoByUserEmail("direct@example.com");
        assertNotNull(result2);

        // no match
        when(userProviderRepository.findByProviderId("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertTrue(fineService.getFinesDtoByUserEmail("missing@example.com").isEmpty());
    }

    @Test
    @DisplayName("getFinesByUserId and getPendingFinesByUserId should return entity lists")
    void testGetFinesAndPendingFinesByUserId() {
        Long userId = 20L;
        String userUuid = UUID.randomUUID().toString();
        User user = User.builder().id(userId).uuid(userUuid).build();
        Fine fine = Fine.builder().id(1L).userUuid(userUuid).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fineRepository.findByUserUuid(userUuid)).thenReturn(List.of(fine));
        when(fineRepository.findByUserUuidAndStatus(userUuid, FineStatus.PENDING)).thenReturn(List.of(fine));

        assertEquals(1, fineService.getFinesByUserId(userId).size());
        assertEquals(1, fineService.getPendingFinesByUserId(userId).size());
    }

    @Test
    @DisplayName("mapToDtoWithDetails heals zeroed fine amount from borrow record if available")
    void testMapToDtoWithDetails_healsZeroAmount() {
        String bookUuid = UUID.randomUUID().toString();
        String userUuid = UUID.randomUUID().toString();
        Fine fine = Fine.builder()
                .id(1L)
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PAID)
                .build();
        Borrow borrow = Borrow.builder()
                .dueDate(LocalDate.now().minusDays(3))
                .build();

        when(borrowRepository.findFirstByBook_UuidAndUser_UuidOrderByDueDateDesc(bookUuid, userUuid))
                .thenReturn(Optional.of(borrow));

        var dto = fineService.mapToDtoWithDetails(fine);
        assertNotNull(dto);
        assertEquals(BigDecimal.valueOf(15.0), dto.pendingFineAmount());
    }

    @Test
    @DisplayName("mapToDtoWithDetails when null fine, or null book/user, or healing throws exception")
    void testMapToDtoWithDetails_edgeCases() {
        // null fine
        assertNull(fineService.mapToDtoWithDetails(null));

        // non-null bookUuid and userUuid but repos return empty
        String bUuid = UUID.randomUUID().toString();
        String uUuid = UUID.randomUUID().toString();
        when(bookRepository.findByUuid(bUuid)).thenReturn(Optional.empty());
        when(userRepository.findByUuid(uUuid)).thenReturn(Optional.empty());

        Fine fineWithMissingEntities = Fine.builder()
                .id(10L)
                .bookUuid(bUuid)
                .userUuid(uUuid)
                .pendingFineAmount(BigDecimal.valueOf(15.0))
                .status(FineStatus.PENDING)
                .build();

        FineResponseDto missingDto = fineService.mapToDtoWithDetails(fineWithMissingEntities);
        assertNotNull(missingDto);
        assertEquals("Library Book", missingDto.bookTitle());
        assertEquals("Library Member", missingDto.userName());

        // null bookUuid & userUuid, null pendingFineAmount
        Fine fineNullUuids = Fine.builder()
                .id(2L)
                .bookUuid(null)
                .userUuid(null)
                .pendingFineAmount(null)
                .status(FineStatus.PENDING)
                .build();
        FineResponseDto dto1 = fineService.mapToDtoWithDetails(fineNullUuids);
        assertNotNull(dto1);
        assertEquals(BigDecimal.valueOf(10.0), dto1.amount());
        assertEquals("Library Book", dto1.bookTitle());
        assertEquals("Library Member", dto1.userName());

        // fine where bookUuid is null but userUuid is non-null
        Fine fineOnlyUser = Fine.builder()
                .id(12L)
                .bookUuid(null)
                .userUuid(uUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PENDING)
                .build();
        FineResponseDto dtoOnlyUser = fineService.mapToDtoWithDetails(fineOnlyUser);
        assertNotNull(dtoOnlyUser);
        assertEquals(BigDecimal.valueOf(10.0), dtoOnlyUser.amount());

        // fine where bookUuid is non-null but userUuid is null
        Fine fineOnlyBook = Fine.builder()
                .id(13L)
                .bookUuid(bUuid)
                .userUuid(null)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PENDING)
                .build();
        FineResponseDto dtoOnlyBook = fineService.mapToDtoWithDetails(fineOnlyBook);
        assertNotNull(dtoOnlyBook);
        assertEquals(BigDecimal.valueOf(10.0), dtoOnlyBook.amount());

        // borrowOpt is present but fine is 0
        String bookUuid = UUID.randomUUID().toString();
        String userUuid = UUID.randomUUID().toString();
        Fine fineZeroFineBorrow = Fine.builder()
                .id(14L)
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PENDING)
                .build();
        Borrow notOverdueBorrow = Borrow.builder()
                .dueDate(LocalDate.now().plusDays(2))
                .build();
        when(borrowRepository.findFirstByBook_UuidAndUser_UuidOrderByDueDateDesc(bookUuid, userUuid))
                .thenReturn(Optional.of(notOverdueBorrow));
        FineResponseDto dtoZeroFineBorrow = fineService.mapToDtoWithDetails(fineZeroFineBorrow);
        assertNotNull(dtoZeroFineBorrow);
        assertEquals(BigDecimal.valueOf(10.0), dtoZeroFineBorrow.amount());

        // healing save throws exception
        Fine fineSaveThrows = Fine.builder()
                .id(3L)
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.ZERO)
                .status(FineStatus.PENDING)
                .build();
        Borrow borrow = Borrow.builder()
                .dueDate(LocalDate.now().minusDays(2))
                .build();
        when(borrowRepository.findFirstByBook_UuidAndUser_UuidOrderByDueDateDesc(bookUuid, userUuid))
                .thenReturn(Optional.of(borrow));
        when(fineRepository.save(any(Fine.class))).thenThrow(new RuntimeException("DB save error during healing"));

        FineResponseDto dto2 = fineService.mapToDtoWithDetails(fineSaveThrows);
        assertNotNull(dto2);
        assertEquals(BigDecimal.valueOf(10.0), dto2.amount());
    }
}
