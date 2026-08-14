package com.kovanlabs.librarymanagement.borrow.service;

import com.kovanlabs.librarymanagement.book.dto.PagedResponse;
import com.kovanlabs.librarymanagement.book.entity.Book;
import com.kovanlabs.librarymanagement.book.repository.BookRepository;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.borrow.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.borrow.entity.Borrow;
import com.kovanlabs.librarymanagement.borrow.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.borrow.repository.BorrowRepository;
import com.kovanlabs.librarymanagement.notification.factory.NotificationFactory;
import com.kovanlabs.librarymanagement.user.entity.Users;
import com.kovanlabs.librarymanagement.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BorrowServiceImplTest {

    @Mock
    private BorrowRepository borrowRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationFactory notificationFactory;

    @InjectMocks
    private BorrowServiceImpl borrowService;

    @Test
    @DisplayName("searchBorrowedBooks should return paged borrowed book responses")
    void searchBorrowedBooks_ShouldReturnPagedBorrowResponses() {
        Users user = Users.builder()
                .id(2L)
                .name("Jane Reader")
                .email("jane@example.com")
                .build();

        Book book = Book.builder()
                .id(3L)
                .title("Clean Code")
                .author("Robert C. Martin")
                .isbn("9780132350884")
                .build();

        Borrow borrow = Borrow.builder()
                .id(1L)
                .users(user)
                .book(book)
                .borrowDate(LocalDate.of(2026, 8, 1))
                .dueDate(LocalDate.of(2026, 8, 15))
                .status(BorrowStatus.BORROWED)
                .build();

        Page<Borrow> borrowPage = new PageImpl<>(
                List.of(borrow),
                PageRequest.of(0, 10, Sort.by("id").ascending()),
                1
        );

        when(borrowRepository.searchBorrowedBooks(eq("%clean%"), any())).thenReturn(borrowPage);

        PagedResponse<BorrowResponseDto> response = borrowService.searchBorrowedBooks("clean", 0, 10, "id", "asc");

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals(1L, response.content().get(0).borrowId());
        assertEquals(3L, response.content().get(0).bookId());
        assertEquals(0, response.pageNo());
        assertEquals(1L, response.totalElements());
    }

    @Test
    @DisplayName("searchBorrowedBooks with invalid sortBy should throw ResponseStatusException BAD_REQUEST")
    void searchBorrowedBooks_WithInvalidSortBy_ShouldThrowResponseStatusException() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> borrowService.searchBorrowedBooks("clean", 0, 10, "invalidField", "asc")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    @DisplayName("searchBorrowedBooks with invalid sortDir should throw ResponseStatusException BAD_REQUEST")
    void searchBorrowedBooks_WithInvalidSortDir_ShouldThrowResponseStatusException() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> borrowService.searchBorrowedBooks("clean", 0, 10, "id", "INVALID_DIR")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }
}