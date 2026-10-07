package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.salesforce.enums.SObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.SObjectAttributes;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.Objects;
/**
 * MapStruct mapper for Book entity conversions, DTO transformations,
 * and Salesforce Book SObject mappings.
 */
@Mapper(imports = {SObject.class, SObjectAttributes.class, Objects.class})
public interface BookMapper {

    BookMapper INSTANCE = Mappers.getMapper(BookMapper.class);

    // --- Entity <-> DTO ---

    /**
     * Maps a {@link Book} entity to a {@link BookResponse} DTO.
     *
     * @param book The book entity
     * @return The mapped {@link BookResponse} DTO
     */
    @Mapping(target = "availableBookCount", expression = "java((Objects.nonNull(book.getTotalBookCount()) ? book.getTotalBookCount() : 0) - (Objects.nonNull(book.getBorrowedBookCount()) ? book.getBorrowedBookCount() : 0))")
    BookResponse mapToResponse(Book book);

    /**
     * Maps a list of {@link Book} entities to a list of {@link BookResponse} DTOs.
     *
     * @param books List of book entities
     * @return List of mapped {@link BookResponse} DTOs
     */
    List<BookResponse> mapToResponse(List<Book> books);

    /**
     * Maps a {@link BookRequest} DTO to a new {@link Book} entity.
     *
     * @param request The book creation/update request DTO
     * @return The unpersisted {@link Book} entity
     */
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "coverImageKey", ignore = true)
    @Mapping(target = "borrowedBookCount", ignore = true)
    @Mapping(target = "salesforceSyncStatus", ignore = true)
    @Mapping(target = "salesforceRetryCount", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Book mapToEntity(BookRequest request);

    // --- DTO <-> SObject (Salesforce Models) ---

    /**
     * Maps a {@link BookResponse} DTO to a Salesforce {@link BookSObject}.
     *
     * @param dto The book response DTO
     * @return The mapped {@link BookSObject}
     */
    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.BOOK.getObjectName()).build())")
    @Mapping(target = "externalBookUuid", source = "uuid")
    @Mapping(target = "name", source = "title", defaultValue = "Untitled")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    @Mapping(target = "totalBookCount", source = "totalBookCount")
    @Mapping(target = "borrowedBookCount", source = "borrowedBookCount")
    BookSObject toBookSObject(BookResponse dto);

    /**
     * Converts a {@link Book} entity to a {@link BookSObject} model.
     *
     * @param book The Book entity
     * @return The mapped {@link BookSObject}
     */
    @Mapping(target = "attributes", expression = "java(SObjectAttributes.builder().type(SObject.BOOK.getObjectName()).build())")
    @Mapping(target = "externalBookUuid", source = "uuid")
    @Mapping(target = "name", expression = "java((Objects.nonNull(book.getTitle()) && !book.getTitle().isBlank()) ? book.getTitle() : \"Untitled\")")
    @Mapping(target = "title", expression = "java((Objects.nonNull(book.getTitle()) && !book.getTitle().isBlank()) ? book.getTitle() : \"Untitled\")")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    @Mapping(target = "totalBookCount", source = "totalBookCount")
    @Mapping(target = "borrowedBookCount", source = "borrowedBookCount")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "errors", ignore = true)
    BookSObject toBookSObject(Book book);

    /**
     * Maps a Salesforce {@link BookSObject} to a {@link BookResponse} DTO.
     *
     * @param sObject The book SObject from Salesforce
     * @return The mapped {@link BookResponse} DTO
     */
    @Mapping(target = "uuid", source = "externalBookUuid")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "title", defaultExpression = "java(sObject.getName())")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    @Mapping(target = "totalBookCount", source = "totalBookCount")
    @Mapping(target = "borrowedBookCount", source = "borrowedBookCount")
    @Mapping(target = "availableBookCount", expression = "java((Objects.nonNull(sObject.getTotalBookCount()) ? sObject.getTotalBookCount() : 0) - (Objects.nonNull(sObject.getBorrowedBookCount()) ? sObject.getBorrowedBookCount() : 0))")
    BookResponse toBookResponse(BookSObject sObject);

    /**
     * Maps a list of {@link BookSObject} records to a list of {@link BookResponse} DTOs.
     *
     * @param sObjects List of Book SObjects
     * @return List of mapped {@link BookResponse} DTOs
     */
    List<BookResponse> toBookResponseList(List<BookSObject> sObjects);
}
