package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.SObjectAttributes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * MapStruct mapper for Borrow entity conversions, DTO transformations,
 * and Salesforce Borrow SObject mappings using static INSTANCE.
 */
@Mapper(imports = {LocalDate.class, BorrowStatus.class, SObject.class, SObjectAttributes.class})
public interface BorrowMapper {

    BorrowMapper INSTANCE = Mappers.getMapper(BorrowMapper.class);

    // --- Entity <-> DTO ---

    /**
     * Maps a {@link Borrow} record and its nested relationships to a {@link BorrowResponseDto}.
     *
     * @param borrow The borrow entity
     * @return The mapped {@link BorrowResponseDto}
     */
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

    /**
     * Maps a list of {@link Borrow} entities to a list of {@link BorrowResponseDto}s.
     *
     * @param borrows List of borrow entities
     * @return List of mapped {@link BorrowResponseDto}s
     */
    List<BorrowResponseDto> mapToResponseForBorrows(List<Borrow> borrows);

    /**
     * Constructs a new {@link Borrow} entity associated with a given Book and User.
     *
     * @param request The borrow request DTO
     * @param book The book being borrowed
     * @param user The user borrowing the book
     * @return The populated {@link Borrow} entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "book", source = "book")
    @Mapping(target = "user", source = "user")
    @Mapping(target = "borrowDate", expression = "java(LocalDate.now())")
    @Mapping(target = "dueDate", expression = "java(LocalDate.now().plusDays(14))")
    @Mapping(target = "returnedDate", ignore = true)
    @Mapping(target = "status", expression = "java(BorrowStatus.BORROWED)")
    @Mapping(target = "rewardProcessed", ignore = true)
    Borrow mapToEntity(BorrowRequestDto request, Book book, User user);

    // --- DTO <-> SObject (Salesforce Models) ---

    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.BORROW.getObjectName()).build())")
    @Mapping(target = "externalBorrowUuid", source = "borrowUuid")
    @Mapping(target = "borrowDate", source = "borrowDate")
    @Mapping(target = "dueDate", source = "dueDate")
    @Mapping(target = "returnDate", source = "returnedDate")
    @Mapping(target = "borrowStatus", source = "status")
    @Mapping(target = "contact", source = "dto")
    @Mapping(target = "book", source = "dto")
    BorrowSObject toBorrowSObject(BorrowResponseDto dto);

    /**
     * Maps a {@link BorrowResponseDto} to a nested {@link ContactSObject}.
     *
     * @param dto The borrow response DTO
     * @return The mapped {@link ContactSObject}
     */
    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.CONTACT.getObjectName()).build())")
    @Mapping(target = "externalUserUuid", source = "userId")
    @Mapping(target = "lastName", source = "userName")
    @Mapping(target = "email", source = "userEmail")
    ContactSObject toContactSObject(BorrowResponseDto dto);

    /**
     * Maps a {@link BorrowResponseDto} to a nested {@link BookSObject}.
     *
     * @param dto The borrow response DTO
     * @return The mapped {@link BookSObject}
     */
    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.BOOK.getObjectName()).build())")
    @Mapping(target = "externalBookUuid", source = "bookId")
    @Mapping(target = "title", source = "bookTitle")
    @Mapping(target = "name", source = "bookTitle", defaultValue = "Untitled")
    @Mapping(target = "author", source = "bookAuthor")
    @Mapping(target = "coverImageUrl", source = "bookCoverImageUrl")
    @Mapping(target = "isbn", ignore = true)
    BookSObject toBookSObject(BorrowResponseDto dto);

    /**
     * Maps a Salesforce {@link BorrowSObject} to a {@link BorrowResponseDto}.
     *
     * @param sObject The borrow SObject
     * @return The mapped {@link BorrowResponseDto}
     */
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

    /**
     * Maps a list of {@link BorrowSObject} records to a list of {@link BorrowResponseDto}s.
     *
     * @param sObjects List of Borrow SObjects
     * @return List of mapped {@link BorrowResponseDto}s
     */
    List<BorrowResponseDto> toBorrowResponseList(List<BorrowSObject> sObjects);

    // --- Helper Mapping Methods ---

    /**
     * Resolves the title of a book from either the {@code title} or {@code name} field.
     *
     * @param book The Book SObject
     * @return The resolved book title
     */
    @Named("resolveBookTitle")
    default String resolveBookTitle(BookSObject book) {
        if (book == null) return null;
        return (book.getTitle() != null && !book.getTitle().isBlank()) ? book.getTitle() : book.getName();
    }

    /**
     * Safely parses a UUID string into a {@link UUID}.
     *
     * @param str The UUID string
     * @return Parsed {@link UUID} or {@code null}
     */
    @Named("parseUUID")
    default UUID parseUUID(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safely parses an ISO date string into a {@link LocalDate}.
     *
     * @param str The ISO date string (YYYY-MM-DD)
     * @return Parsed {@link LocalDate} or {@code null}
     */
    @Named("parseLocalDate")
    default LocalDate parseLocalDate(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return LocalDate.parse(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Safely converts a status string into a {@link BorrowStatus} enum value.
     *
     * @param statusStr The string status representation
     * @return The {@link BorrowStatus} enum constant or {@code null}
     */
    @Named("parseBorrowStatus")
    default BorrowStatus parseBorrowStatus(String statusStr) {
        if (statusStr == null || statusStr.isBlank()) return null;
        try {
            return BorrowStatus.valueOf(statusStr);
        } catch (Exception e) {
            return null;
        }
    }
}
