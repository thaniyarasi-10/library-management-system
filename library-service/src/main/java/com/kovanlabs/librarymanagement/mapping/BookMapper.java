package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import java.time.LocalDate;
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

    @Mapping(target = "externalBookUuid", source = "uuid")
    @Mapping(target = "name", source = "title", defaultValue = "Untitled")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    BookSObject toBookSObject(BookResponse dto);

    @Mapping(target = "uuid", source = "externalBookUuid", qualifiedByName = "parseUUID")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "title", defaultExpression = "java(sObject.getName())")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    BookResponse toBookResponse(BookSObject sObject);

    List<BookResponse> toBookResponseList(List<BookSObject> sObjects);

    @Mapping(target = "externalBorrowUuid", source = "borrowUuid")
    @Mapping(target = "borrowDate", source = "borrowDate")
    @Mapping(target = "dueDate", source = "dueDate")
    @Mapping(target = "returnDate", source = "returnedDate")
    @Mapping(target = "borrowStatus", source = "status")
    @Mapping(target = "contact", source = "dto")
    @Mapping(target = "book", source = "dto")
    BorrowSObject toBorrowSObject(BorrowResponseDto dto);

    @Mapping(target = "externalUserUuid", source = "userId")
    @Mapping(target = "lastName", source = "userName")
    @Mapping(target = "email", source = "userEmail")
    ContactSObject toContactSObject(BorrowResponseDto dto);

    @Mapping(target = "externalBookUuid", source = "bookId")
    @Mapping(target = "title", source = "bookTitle")
    @Mapping(target = "name", source = "bookTitle", defaultValue = "Untitled")
    @Mapping(target = "author", source = "bookAuthor")
    @Mapping(target = "coverImageUrl", source = "bookCoverImageUrl")
    @Mapping(target = "isbn", ignore = true)
    BookSObject toBookSObject(BorrowResponseDto dto);

    @Mapping(target = "borrowUuid", source = "externalBorrowUuid", qualifiedByName = "parseUUID")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "userId", source = "contact.externalUserUuid", qualifiedByName = "parseUUID")
    @Mapping(target = "userName", source = "contact.lastName")
    @Mapping(target = "userEmail", source = "contact.email")
    @Mapping(target = "bookId", source = "book.externalBookUuid", qualifiedByName = "parseUUID")
    @Mapping(target = "bookNumericId", ignore = true)
    @Mapping(target = "bookTitle", source = "book", qualifiedByName = "resolveBookTitle")
    @Mapping(target = "bookAuthor", source = "book.author")
    @Mapping(target = "bookCoverImageUrl", source = "book.coverImageUrl")
    @Mapping(target = "userNumericId", ignore = true)
    @Mapping(target = "borrowDate", source = "borrowDate", qualifiedByName = "parseLocalDate")
    @Mapping(target = "dueDate", source = "dueDate", qualifiedByName = "parseLocalDate")
    @Mapping(target = "returnedDate", source = "returnDate", qualifiedByName = "parseLocalDate")
    @Mapping(target = "status", source = "borrowStatus", qualifiedByName = "parseBorrowStatus")
    BorrowResponseDto toBorrowResponse(BorrowSObject sObject);

    List<BorrowResponseDto> toBorrowResponseList(List<BorrowSObject> sObjects);

    // --- Helper Mapping Methods ---

    default String resolveBookTitle(BookSObject book) {
        if (book == null) return null;
        return (book.getTitle() != null && !book.getTitle().isBlank()) ? book.getTitle() : book.getName();
    }

    default UUID parseUUID(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }

    default LocalDate parseLocalDate(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDate.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    default BorrowStatus parseBorrowStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) return null;
        try {
            return BorrowStatus.valueOf(statusStr);
        } catch (Exception e) {
            return null;
        }
    }
}
