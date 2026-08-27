package com.kovanlabs.librarymanagement.book.service;

import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.book.mapping.BookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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
    private UserFineChecker userFineChecker;

    @Spy
    private BookMapper bookMapper = Mappers.getMapper(BookMapper.class);

    @InjectMocks
    private BorrowServiceImpl borrowService;

    private User user;
    private Book book;
    private Borrow borrow;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .uuid(UUID.randomUUID())
                .id(1L)
                .name("John Doe")
                .email("john@example.com")
                .build();

        book = Book.builder()
                .uuid(UUID.randomUUID())
                .id(10L)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .isbn("9780134494166")
                .build();

        borrow = Borrow.builder()
                .uuid(UUID.randomUUID())
                .id(100L)
                .book(book)
                .user(user)
                .borrowDate(LocalDate.now().minusDays(5))
                .dueDate(LocalDate.now().plusDays(9))
                .status(BorrowStatus.BORROWED)
                .build();
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
            b.setUuid(UUID.randomUUID());
            b.setId(100L);
            return b;
        });

        BorrowResponseDto response = borrowService.borrowBook(request);

        assertNotNull(response);
        assertEquals(BorrowStatus.BORROWED, response.status());
        verify(userFineChecker, times(1)).hasPendingFines(1L);
        verify(borrowRepository, times(1)).save(any(Borrow.class));
    }

    @Test
    @DisplayName("returnBook should succeed when user has no pending fines")
    void returnBook_WhenNoPendingFines_ShouldSucceed() {
        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = borrowService.returnBook(100L);

        assertNotNull(response);
        assertEquals(BorrowStatus.RETURNED, response.status());
        assertNotNull(response.returnedDate());
        verify(userFineChecker, times(1)).hasPendingFines(1L);
        verify(borrowRepository, times(1)).save(borrow);
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
    @DisplayName("borrowBook validation failures (null request, missing user, missing book)")
    void borrowBook_ValidationFailures() {
        // Null request
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(null));
        // Null bookId
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(null, 1L)));
        // Null userId
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(10L, null)));

        // User not found
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(10L, 99L)));

        // Book not found
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userFineChecker.hasPendingFines(1L)).thenReturn(false);
        when(bookRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> borrowService.borrowBook(new BorrowRequestDto(99L, 1L)));
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
        BorrowServiceImpl serviceWithoutFineChecker = new BorrowServiceImpl(borrowRepository, bookRepository, userRepository, null, bookMapper);

        BorrowRequestDto request = new BorrowRequestDto(10L, 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
        when(borrowRepository.save(any(Borrow.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowResponseDto response = serviceWithoutFineChecker.borrowBook(request);
        assertNotNull(response);

        when(borrowRepository.findById(100L)).thenReturn(Optional.of(borrow));
        BorrowResponseDto returnResponse = serviceWithoutFineChecker.returnBook(100L);
        assertNotNull(returnResponse);
    }
}
