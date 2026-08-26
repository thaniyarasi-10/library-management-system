package com.kovanlabs.librarymanagement.book.mapping;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BookMapping {

    public static BookResponse mapToResponse(Book book) {
        if (book == null) {
            return null;
        }
        return new BookResponse(
                book.getUuid(),
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn()
        );
    }

    public static List<BookResponse> mapToResponse(List<Book> books) {
        if (books == null) {
            return Collections.emptyList();
        }
        return books.stream()
                .map(BookMapping::mapToResponse)
                .collect(Collectors.toList());
    }

    public static BorrowResponseDto mapToResponse(Borrow borrow) {
        if (borrow == null) {
            return null;
        }
        return BorrowResponseDto.builder()
                .borrowUuid(borrow.getUuid())
                .id(borrow.getId())
                .bookId(borrow.getBook() != null ? borrow.getBook().getUuid() : null)
                .userId(borrow.getUser() != null ? borrow.getUser().getUuid() : null)
                .borrowDate(borrow.getBorrowDate())
                .dueDate(borrow.getDueDate())
                .returnedDate(borrow.getReturnedDate())
                .status(borrow.getStatus())
                .build();
    }

    public static List<BorrowResponseDto> mapToResponseForBorrows(List<Borrow> borrows) {
        if (borrows == null) {
            return Collections.emptyList();
        }
        return borrows.stream()
                .map(BookMapping::mapToResponse)
                .collect(Collectors.toList());
    }

    public static Book mapToEntity(BookRequest request) {
        if (request == null) {
            return null;
        }
        return Book.builder()
                .title(request.title())
                .author(request.author())
                .isbn(request.isbn())
                .build();
    }

    public static Borrow mapToEntity(BorrowRequestDto request, Book book, User user) {
        if (request == null && book == null && user == null) {
            return null;
        }
        return Borrow.builder()
                .book(book)
                .user(user)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();
    }
}

