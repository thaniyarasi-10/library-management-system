package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSyncImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BorrowServiceImplTest {

    @Mock
    private BorrowRepository borrowRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private UserFineChecker userFineChecker;

    @Mock
    private MembershipService membershipService;

    @Mock
    private RewardService rewardService;

    @Mock
    private SalesforceSyncImpl salesforceSyncService;

    @InjectMocks
    private BorrowServiceImpl borrowService;

    private User user;
    private Book book;
    private Borrow borrow;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .uuid(UUID.randomUUID().toString())
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .build();

        book = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(10L)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .isbn("9780134494166")
                .totalBookCount(5)
                .borrowedBookCount(1)
                .build();

        borrow = Borrow.builder()
                .uuid(UUID.randomUUID().toString())
                .id(100L)
                .book(book)
                .user(user)
                .borrowDate(LocalDate.now().minusDays(5))
                .dueDate(LocalDate.now().plusDays(9))
                .status(BorrowStatus.BORROWED)
                .build();

        lenient().when(membershipService.hasActiveMembership(any())).thenReturn(true);
        lenient().when(borrowRepository.countByUser_UuidAndStatus(any(), any())).thenReturn(0L);
        lenient().when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("borrowBook should throw BAD_REQUEST when user already has 5 active borrowed books")
    void borrowBook_WhenUserExceeds5BookLimit_ShouldThrowBadRequest() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(borrowRepository.countByUser_UuidAndStatus(user.getUuid(), BorrowStatus.BORROWED)).thenReturn(5L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Borrow limit exceeded") || ex.getMessage().contains("Borrow limit exceeded"));
        verify(borrowRepository, never()).save(any());
    }

    @Test
    @DisplayName("borrowBook should succeed when user has no pending fines")
    void borrowBook_WhenNoPendingFines_ShouldSucceed() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> {
            Borrow b = inv.getArgument(0);
            b.setUuid(UUID.randomUUID().toString());
            b.setId(100L);
            return b;
        });

        BorrowResponseDto response = borrowService.borrowBook(request);

        assertNotNull(response);
        assertEquals(BorrowStatus.BORROWED, response.status());
        verify(userFineChecker, times(1)).hasPendingFines(1L);
        verify(borrowRepository, times(2)).save(any(Borrow.class));
    }

    @Test
    @DisplayName("borrowBook should set status SUCCESS when salesforce sync succeeds")
    void borrowBook_whenSalesforceSyncSucceeds_shouldMarkStatusSuccess() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.borrowBook(request);

        assertNotNull(response);
        verify(salesforceSyncService).syncBorrow(any());
        verify(borrowRepository, atLeast(2)).save(any(Borrow.class));
    }

    @Test
    @DisplayName("borrowBook should increment retry count when salesforce sync fails")
    void borrowBook_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SF Error")).when(salesforceSyncService).syncBorrow(any());

        BorrowResponseDto response = borrowService.borrowBook(request);

        assertNotNull(response);
        verify(borrowRepository, atLeast(2)).save(any(Borrow.class));
    }

    @Test
    @DisplayName("returnBook should succeed when user has no pending fines and increment bookCount")
    void returnBook_WhenNoPendingFines_ShouldSucceed() {
        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.returnBook(100L);

        assertNotNull(response);
        assertEquals(BorrowStatus.RETURNED, response.status());
        assertNotNull(response.returnedDate());
        assertEquals(0, book.getBorrowedBookCount());
        verify(bookRepository, times(2)).save(book);
        verify(salesforceSyncService).syncBook(any());
        verify(userFineChecker, times(1)).hasPendingFines(1L);
        verify(borrowRepository, times(2)).save(borrow);
        assertEquals(com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus.SUCCESS, borrow.getSalesforceSyncStatus());
    }

    @Test
    @DisplayName("returnBook when borrow.getBook() is null and borrow.getUser() is null")
    void returnBook_whenBookAndUserNull() {
        Borrow borrowWithoutBookAndUser = Borrow.builder()
                .id(200L)
                .uuid(UUID.randomUUID().toString())
                .status(BorrowStatus.BORROWED)
                .build();
        when(borrowRepository.findById(200L)).thenReturn(Optional.of(borrowWithoutBookAndUser));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.returnBook(200L);
        assertNotNull(response);
        assertEquals(BorrowStatus.RETURNED, response.status());
    }

    @Test
    @DisplayName("returnBook when book borrowedBookCount is null should decrement cleanly to 0")
    void returnBook_whenBorrowedBookCountNull() {
        book.setBorrowedBookCount(null);
        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.returnBook(100L);
        assertNotNull(response);
        assertEquals(0, book.getBorrowedBookCount());
    }

    @Test
    @DisplayName("returnBook when already returned should throw BAD_REQUEST")
    void returnBook_whenAlreadyReturned_shouldThrowBadRequest() {
        Borrow alreadyReturned = Borrow.builder()
                .uuid(UUID.randomUUID().toString())
                .id(101L)
                .book(book)
                .user(user)
                .status(BorrowStatus.RETURNED)
                .build();
        when(borrowRepository.findById(101L)).thenReturn(Optional.of(alreadyReturned));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> borrowService.returnBook(101L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("already been returned"));
    }

    @Test
    @DisplayName("returnBook should increment retry count when salesforce sync fails")
    void returnBook_whenSalesforceSyncFails_shouldIncrementRetryAndKeepPending() {
        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SF Return Error")).when(salesforceSyncService).syncBorrow(any());

        BorrowResponseDto response = borrowService.returnBook(100L);

        assertNotNull(response);
        assertEquals(BorrowStatus.RETURNED, response.status());
        assertEquals(com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus.PENDING, borrow.getSalesforceSyncStatus());
        assertEquals(1, borrow.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("borrowBook should throw ResponseStatusException when user has pending fines")
    void borrowBook_WhenUserHasPendingFines_ShouldThrowException() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(request));
        assertTrue(ex.getMessage().contains("User has pending fines"));
        verify(borrowRepository, never()).save(any());
    }

    @Test
    @DisplayName("returnBook should throw ResponseStatusException when user has pending fines")
    void returnBook_WhenUserHasPendingFines_ShouldThrowException() {
        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(true);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> borrowService.returnBook(100L));
        assertTrue(ex.getMessage().contains("User has pending fines"));
        verify(borrowRepository, never()).save(any());
    }

    @Test
    @DisplayName("borrowBook validation failures (null request, missing user, missing book, blank identifiers)")
    void borrowBook_ValidationFailures() {
        // Null request
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(null));
        // Null bookId with blank bookUuid
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(null, 1L, "   ", null)));
        // Null userId and null userIdentifier with blank userUuid
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(10L, null, null, "   ")));
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(10L, null), "   "));

        // User not found by ID
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(10L, 99L)));

        // User not found by userIdentifier
        when(userProviderRepository.findByProviderId("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(10L, null), "unknown@example.com"));

        // Book not found
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(99L, 1L)));
    }

    @Test
    @DisplayName("borrowBook with blank bookUuid and userUuid falling back to IDs")
    void borrowBook_withBlankUuids_fallsBackToIds() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L, "   ", "   ");

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.borrowBook(request);
        assertNotNull(response);
    }

    @Test
    @DisplayName("borrowBook should resolve user from userIdentifier via provider")
    void borrowBook_WhenUserIdNull_ShouldResolveFromUserIdentifierProvider() {
        BorrowRequestDto request = new BorrowRequestDto(10L, null);
        String providerId = "auth0|provider123";

        com.kovanlabs.librarymanagement.database.entity.UserProvider up = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(user.getUuid())
                .build();
        when(userProviderRepository.findByProviderId(providerId)).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> {
            Borrow b = inv.getArgument(0);
            b.setUuid(UUID.randomUUID().toString());
            b.setId(100L);
            return b;
        });

        BorrowResponseDto response = borrowService.borrowBook(request, providerId);

        assertNotNull(response);
        assertEquals(BorrowStatus.BORROWED, response.status());
    }

    @Test
    @DisplayName("borrowBook should resolve user from userIdentifier when userId is null")
    void borrowBook_WhenUserIdNull_ShouldResolveFromUserIdentifier() {
        BorrowRequestDto request = new BorrowRequestDto(10L, null);
        String email = "john@example.com";

        when(userProviderRepository.findByProviderId(email)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> {
            Borrow b = inv.getArgument(0);
            b.setUuid(UUID.randomUUID().toString());
            b.setId(100L);
            return b;
        });

        BorrowResponseDto response = borrowService.borrowBook(request, email);

        assertNotNull(response);
        assertEquals(BorrowStatus.BORROWED, response.status());
        verify(userRepository).findByEmail(email);
        verify(userFineChecker).hasPendingFines(1L);
    }

    @Test
    @DisplayName("borrowBook should throw BAD_REQUEST when borrowed count equals total book count or total is null/zero")
    void borrowBook_WhenBorrowCountEqualsTotalBook_ShouldThrowBadRequest() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);
        Book outOfStockBook = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(10L)
                .title("Zero Stock Book")
                .author("Author")
                .isbn("12345")
                .totalBookCount(2)
                .borrowedBookCount(2)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(outOfStockBook));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("No copies available") || ex.getMessage().contains("No copies available"));
        verify(borrowRepository, never()).save(any());
        verify(salesforceSyncService, never()).syncBook(any());

        // Test with null totalBookCount
        outOfStockBook.setTotalBookCount(null);
        outOfStockBook.setBorrowedBookCount(null);
        ResponseStatusException exNull = assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(request));
        assertEquals(HttpStatus.BAD_REQUEST, exNull.getStatusCode());
    }

    @Test
    @DisplayName("borrowBook should increment borrowed_book_count by 1 and sync to Salesforce")
    void borrowBook_ShouldIncrementBorrowedBookCountAndSyncSalesforce() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);
        book.setTotalBookCount(5);
        book.setBorrowedBookCount(1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.borrowBook(request);

        assertNotNull(response);
        assertEquals(2, book.getBorrowedBookCount());
        verify(bookRepository, atLeastOnce()).save(book);
        verify(salesforceSyncService).syncBook(argThat(sObj -> Objects.equals(sObj.getBorrowedBookCount(), 2)));
    }

    @Test
    @DisplayName("returnBook validation failures (null borrowId, record not found)")
    void returnBook_ValidationFailures() {
        // Null borrowId
        assertThrows(ResponseStatusException.class, () -> borrowService.returnBook(null));

        // Borrow record not found
        when(borrowRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.returnBook(999L));
    }

    @Test
    @DisplayName("borrowBook and returnBook with null userFineChecker should succeed")
    void borrowAndReturnBook_withNullUserFineChecker_ShouldSucceed() {

        BorrowServiceImpl serviceWithoutFineChecker =
                new BorrowServiceImpl(
                        borrowRepository,
                        bookRepository,
                        userRepository,
                        userProviderRepository,
                        null,
                        membershipService,
                        null,
                        rewardService
                );

        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = serviceWithoutFineChecker.borrowBook(request);

        assertNotNull(response);

        // Also test borrowBook(request, userIdentifier) with null fine checker
        when(userProviderRepository.findByProviderId("john@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        BorrowResponseDto respIdent = serviceWithoutFineChecker.borrowBook(new BorrowRequestDto(10L, null), "john@example.com");
        assertNotNull(respIdent);

        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));

        BorrowResponseDto returnResponse =
                serviceWithoutFineChecker.returnBook(100L);

        assertNotNull(returnResponse);
    }

    @Test
    @DisplayName("borrowBook should throw FORBIDDEN when user has no active membership")
    void borrowBook_withoutActiveMembership_shouldThrowForbidden() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(membershipService.hasActiveMembership(user.getUuid())).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(request));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(borrowRepository, never()).save(any());
    }

    @Test
    @DisplayName("borrowBook and returnBook when Salesforce throws exception should set status PENDING")
    void borrowAndReturnBook_whenSalesforceThrows_shouldSetPending() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SF create error")).when(salesforceSyncService).syncBorrow(any());

        BorrowResponseDto borrowResp = borrowService.borrowBook(request);
        assertNotNull(borrowResp);

        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));
        doThrow(new RuntimeException("SF return error")).when(salesforceSyncService).syncBorrow(any());

        BorrowResponseDto returnResp = borrowService.returnBook(100L);
        assertNotNull(returnResp);
    }

    @Test
    @DisplayName("getAllBorrows and getBorrowsByUserId should return mapped DTOs")
    void testGetAllBorrowsAndByUserId() {
        when(borrowRepository.findAllByOrderByIdDesc()).thenReturn(List.of(borrow));
        when(borrowRepository.findByUser_IdOrderByIdDesc(1L)).thenReturn(List.of(borrow));

        assertEquals(1, borrowService.getAllBorrows().size());
        assertEquals(1, borrowService.getBorrowsByUserId(1L).size());
        assertTrue(borrowService.getBorrowsByUserId(null).isEmpty());
    }

    @Test
    @DisplayName("getBorrowsByUserEmail should return user borrows or empty")
    void testGetBorrowsByUserEmail() {
        assertTrue(borrowService.getBorrowsByUserEmail(null).isEmpty());

        String email = "john@example.com";
        com.kovanlabs.librarymanagement.database.entity.UserProvider up = com.kovanlabs.librarymanagement.database.entity.UserProvider.builder()
                .userUuid(user.getUuid())
                .build();
        when(userProviderRepository.findByProviderId(email)).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(borrowRepository.findByUser_IdOrderByIdDesc(1L)).thenReturn(List.of(borrow));

        assertEquals(1, borrowService.getBorrowsByUserEmail(email).size());

        // provider match with user.id == null
        String providerEmail = "provider-only@example.com";
        User userNullId = User.builder().id(null).uuid("null-id-uuid").build();
        when(userProviderRepository.findByProviderId(providerEmail)).thenReturn(Optional.of(com.kovanlabs.librarymanagement.database.entity.UserProvider.builder().userUuid("null-id-uuid").build()));
        when(userRepository.findByUuid("null-id-uuid")).thenReturn(Optional.of(userNullId));
        assertTrue(borrowService.getBorrowsByUserEmail(providerEmail).isEmpty());

        // fallback to findByEmail
        when(userProviderRepository.findByProviderId("other@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(user));
        assertEquals(1, borrowService.getBorrowsByUserEmail("other@example.com").size());

        // not found
        when(userProviderRepository.findByProviderId("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        assertTrue(borrowService.getBorrowsByUserEmail("unknown@example.com").isEmpty());
    }

    @Test
    @DisplayName("borrowBook with bookUuid and userUuid directly should succeed")
    void borrowBook_withBookUuidAndUserUuid_shouldSucceed() {
        BorrowRequestDto request = new BorrowRequestDto(null, null, book.getUuid(), user.getUuid());

        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(membershipService.hasActiveMembership(user.getUuid())).thenReturn(true);
        when(userFineChecker.hasPendingFines(user.getId())).thenReturn(false);
        when(bookRepository.findByUuid(book.getUuid())).thenReturn(Optional.of(book));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> {
            Borrow b = inv.getArgument(0);
            b.setUuid(UUID.randomUUID().toString());
            b.setId(101L);
            return b;
        });

        BorrowResponseDto response = borrowService.borrowBook(request);

        assertNotNull(response);
        assertEquals(BorrowStatus.BORROWED, response.status());
        verify(userRepository).findByUuid(user.getUuid());
        verify(bookRepository).findByUuid(book.getUuid());
    }

    @Test
    @DisplayName("borrowBook should throw NOT_FOUND when userUuid or bookUuid does not exist")
    void borrowBook_whenUuidNotFound_shouldThrowNotFound() {
        // User UUID not found
        BorrowRequestDto userNotFoundReq = new BorrowRequestDto(null, null, book.getUuid(), "non-existent-user");
        when(userRepository.findByUuid("non-existent-user")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(userNotFoundReq));

        // Book UUID not found
        BorrowRequestDto bookNotFoundReq = new BorrowRequestDto(null, null, "non-existent-book", user.getUuid());
        when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        when(bookRepository.findByUuid("non-existent-book")).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(bookNotFoundReq));
    }

    @Test
    @DisplayName("borrowBook when book sync to salesforce throws exception should continue and mark pending")
    void borrowBook_whenBookSalesforceSyncThrows_shouldContinueAndMarkPending() {
        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        doThrow(new RuntimeException("SF book sync error")).when(salesforceSyncService).syncBook(any());
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.borrowBook(request);

        assertNotNull(response);
        verify(bookRepository, atLeast(2)).save(book);
    }
}
