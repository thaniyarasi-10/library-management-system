package com.kovanlabs.librarymanagement.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.salesforce.constant.BookFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.BorrowFieldConstants;
import com.kovanlabs.librarymanagement.salesforce.constant.ContactFieldConstants;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Mapper(componentModel = "spring")
public interface BookMapper {

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

    default BookResponse mapJsonToBookResponse(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String uuidStr = node.path(BookFieldConstants.EXTERNAL_BOOK_UUID).asText(null);
        String title = node.path(BookFieldConstants.TITLE).asText(null);
        if (title == null || title.isBlank()) {
            title = node.path(BookFieldConstants.NAME).asText(null);
        }
        String author = node.path(BookFieldConstants.AUTHOR).asText(null);
        String isbn = node.path(BookFieldConstants.ISBN).asText(null);
        String coverImageUrl = node.path(BookFieldConstants.COVER_IMAGE_URL).asText(null);

        return new BookResponse(
                parseUUID(uuidStr),
                null,
                title,
                author,
                isbn,
                coverImageUrl
        );
    }

    default List<BookResponse> mapJsonToBookResponseList(JsonNode root) {
        if (root == null || !root.has("records")) {
            return Collections.emptyList();
        }
        List<BookResponse> list = new ArrayList<>();
        for (JsonNode record : root.path("records")) {
            BookResponse resp = mapJsonToBookResponse(record);
            if (resp != null) {
                list.add(resp);
            }
        }
        return list;
    }

    default BorrowResponseDto mapJsonToBorrowResponse(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        String borrowUuidStr = node.path(BorrowFieldConstants.EXTERNAL_BORROW_UUID).asText(null);
        String borrowDateStr = node.path(BorrowFieldConstants.BORROW_DATE).asText(null);
        String dueDateStr = node.path(BorrowFieldConstants.DUE_DATE).asText(null);
        String returnDateStr = node.path(BorrowFieldConstants.RETURN_DATE).asText(null);
        String statusStr = node.path(BorrowFieldConstants.BORROW_STATUS).asText(null);

        JsonNode contactNode = node.path(BorrowFieldConstants.CONTACT_RELATION);
        UUID userUuid = null;
        String userName = null;
        String userEmail = null;
        if (!contactNode.isMissingNode() && !contactNode.isNull()) {
            userUuid = parseUUID(contactNode.path(ContactFieldConstants.EXTERNAL_USER_UUID).asText(null));
            userName = contactNode.path(ContactFieldConstants.LAST_NAME).asText(null);
            userEmail = contactNode.path(ContactFieldConstants.EMAIL).asText(null);
        }

        JsonNode bookNode = node.path(BorrowFieldConstants.BOOK_RELATION);
        UUID bookUuid = null;
        String bookTitle = null;
        String bookAuthor = null;
        String bookCoverUrl = null;
        if (!bookNode.isMissingNode() && !bookNode.isNull()) {
            bookUuid = parseUUID(bookNode.path(BookFieldConstants.EXTERNAL_BOOK_UUID).asText(null));
            bookTitle = bookNode.path(BookFieldConstants.TITLE).asText(null);
            if (bookTitle == null || bookTitle.isBlank()) {
                bookTitle = bookNode.path(BookFieldConstants.NAME).asText(null);
            }
            bookAuthor = bookNode.path(BookFieldConstants.AUTHOR).asText(null);
            bookCoverUrl = bookNode.path(BookFieldConstants.COVER_IMAGE_URL).asText(null);
        }

        BorrowStatus status = null;
        if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = BorrowStatus.valueOf(statusStr);
            } catch (Exception ignored) {}
        }

        return BorrowResponseDto.builder()
                .borrowUuid(parseUUID(borrowUuidStr))
                .userId(userUuid)
                .userName(userName)
                .userEmail(userEmail)
                .bookId(bookUuid)
                .bookTitle(bookTitle)
                .bookAuthor(bookAuthor)
                .bookCoverImageUrl(bookCoverUrl)
                .borrowDate(parseDate(borrowDateStr))
                .dueDate(parseDate(dueDateStr))
                .returnedDate(parseDate(returnDateStr))
                .status(status)
                .build();
    }

    default List<BorrowResponseDto> mapJsonToBorrowResponseList(JsonNode root) {
        if (root == null || !root.has("records")) {
            return Collections.emptyList();
        }
        List<BorrowResponseDto> list = new ArrayList<>();
        for (JsonNode record : root.path("records")) {
            BorrowResponseDto resp = mapJsonToBorrowResponse(record);
            if (resp != null) {
                list.add(resp);
            }
        }
        return list;
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

    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "coverImageKey", ignore = true)
    Book mapToEntity(BookRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "book", source = "book")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "borrowDate", expression = "java(java.time.LocalDate.now())")
    @Mapping(target = "dueDate", expression = "java(java.time.LocalDate.now().plusDays(14))")
    @Mapping(target = "returnedDate", ignore = true)
    @Mapping(target = "status", expression = "java(com.kovanlabs.librarymanagement.database.enums.BorrowStatus.BORROWED)")
    Borrow mapToEntity(BorrowRequestDto request, Book book, User user);
}
