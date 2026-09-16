package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring", imports = {LocalDate.class, BorrowStatus.class})
public interface BookMapper {

    // --- Entity <-> DTO ---

    BookResponse mapToResponse(Book book);

    List<BookResponse> mapToResponse(List<Book> books);

    default List<BookResponse> mapToResponseList(List<Book> books) {
        return mapToResponse(books);
    }

    @Mapping(target = "borrowUuid", source = "uuid")
    @Mapping(target = "bookId", source = "book.uuid")
    @Mapping(target = "userId", source = "user.uuid")
    @Mapping(target = "bookNumericId", source = "book.id")
    @Mapping(target = "bookTitle", source = "book.title")
    @Mapping(target = "bookAuthor", source = "book.author")
    @Mapping(target = "bookCoverImageUrl", source = "book.coverImageUrl")
    @Mapping(target = "userNumericId", source = "user.id")
    @Mapping(target = "userName", source = "user.name")
    @Mapping(target = "userEmail", source = "user.email")
    BorrowResponseDto mapToResponse(Borrow borrow);

    List<BorrowResponseDto> mapToResponseForBorrows(List<Borrow> borrows);

    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "coverImageKey", ignore = true)
    Book mapToEntity(BookRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "book", source = "book")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "borrowDate", expression = "java(LocalDate.now())")
    @Mapping(target = "dueDate", expression = "java(LocalDate.now().plusDays(14))")
    @Mapping(target = "returnedDate", ignore = true)
    @Mapping(target = "status", expression = "java(BorrowStatus.BORROWED)")
    Borrow mapToEntity(BorrowRequestDto request, Book book, User user);

    // --- DTO <-> SObject (Salesforce Models) ---

    default BookSObject toBookSObject(BookResponse dto) {
        if (dto == null) {
            return null;
        }
        String displayName = (dto.title() != null && !dto.title().isBlank())
                ? dto.title()
                : "Untitled";

        return BookSObject.builder()
                .externalBookUuid(dto.uuid() != null ? dto.uuid().toString() : null)
                .name(displayName)
                .title(dto.title())
                .author(dto.author())
                .isbn(dto.isbn())
                .coverImageUrl(dto.coverImageUrl())
                .build();
    }

    default BookResponse toBookResponse(BookSObject sObject) {
        if (sObject == null) {
            return null;
        }
        String title = sObject.getTitle();
        if (title == null || title.isBlank()) {
            title = sObject.getName();
        }
        return new BookResponse(
                parseUUID(sObject.getExternalBookUuid()),
                null,
                title,
                sObject.getAuthor(),
                sObject.getIsbn(),
                sObject.getCoverImageUrl()
        );
    }

    default List<BookResponse> toBookResponseList(List<BookSObject> sObjects) {
        if (sObjects == null) {
            return Collections.emptyList();
        }
        return sObjects.stream()
                .map(this::toBookResponse)
                .toList();
    }

    default BorrowSObject toBorrowSObject(BorrowResponseDto dto) {
        if (dto == null) {
            return null;
        }

        ContactSObject contact = null;
        if (dto.userId() != null || dto.userName() != null || dto.userEmail() != null) {
            contact = ContactSObject.builder()
                    .externalUserUuid(dto.userId() != null ? dto.userId().toString() : null)
                    .lastName(dto.userName())
                    .email(dto.userEmail())
                    .build();
        }

        BookSObject book = null;
        if (dto.bookId() != null || dto.bookTitle() != null || dto.bookAuthor() != null) {
            book = BookSObject.builder()
                    .externalBookUuid(dto.bookId() != null ? dto.bookId().toString() : null)
                    .title(dto.bookTitle())
                    .author(dto.bookAuthor())
                    .coverImageUrl(dto.bookCoverImageUrl())
                    .build();
        }

        return BorrowSObject.builder()
                .externalBorrowUuid(dto.borrowUuid() != null ? dto.borrowUuid().toString() : null)
                .borrowDate(dto.borrowDate() != null ? dto.borrowDate().toString() : null)
                .dueDate(dto.dueDate() != null ? dto.dueDate().toString() : null)
                .returnDate(dto.returnedDate() != null ? dto.returnedDate().toString() : null)
                .borrowStatus(dto.status() != null ? dto.status().name() : null)
                .contact(contact)
                .book(book)
                .build();
    }

    default BorrowResponseDto toBorrowResponse(BorrowSObject sObject) {
        if (sObject == null) {
            return null;
        }

        UUID userUuid = null;
        String userName = null;
        String userEmail = null;
        if (sObject.getContact() != null) {
            userUuid = parseUUID(sObject.getContact().getExternalUserUuid());
            userName = sObject.getContact().getLastName();
            userEmail = sObject.getContact().getEmail();
        }

        UUID bookUuid = null;
        String bookTitle = null;
        String bookAuthor = null;
        String bookCoverUrl = null;
        if (sObject.getBook() != null) {
            bookUuid = parseUUID(sObject.getBook().getExternalBookUuid());
            bookTitle = sObject.getBook().getTitle();
            if (bookTitle == null || bookTitle.isBlank()) {
                bookTitle = sObject.getBook().getName();
            }
            bookAuthor = sObject.getBook().getAuthor();
            bookCoverUrl = sObject.getBook().getCoverImageUrl();
        }

        BorrowStatus status = null;
        if (sObject.getBorrowStatus() != null && !sObject.getBorrowStatus().isBlank()) {
            try {
                status = BorrowStatus.valueOf(sObject.getBorrowStatus());
            } catch (Exception ignored) {}
        }

        return BorrowResponseDto.builder()
                .borrowUuid(parseUUID(sObject.getExternalBorrowUuid()))
                .userId(userUuid)
                .userName(userName)
                .userEmail(userEmail)
                .bookId(bookUuid)
                .bookTitle(bookTitle)
                .bookAuthor(bookAuthor)
                .bookCoverImageUrl(bookCoverUrl)
                .borrowDate(parseDate(sObject.getBorrowDate()))
                .dueDate(parseDate(sObject.getDueDate()))
                .returnedDate(parseDate(sObject.getReturnDate()))
                .status(status)
                .build();
    }

    default List<BorrowResponseDto> toBorrowResponseList(List<BorrowSObject> sObjects) {
        if (sObjects == null) {
            return Collections.emptyList();
        }
        return sObjects.stream()
                .map(this::toBorrowResponse)
                .toList();
    }

    private static UUID parseUUID(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDate(String str) {
        if (str == null || str.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(str);
        } catch (Exception e) {
            return null;
        }
    }
}
