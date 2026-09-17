package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.dto.BookRequest;
import com.kovanlabs.librarymanagement.dto.BookResponse;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.util.List;
import java.util.UUID;

/**
 * MapStruct mapper for Book entity conversions, DTO transformations,
 * and Salesforce Book SObject mappings.
 */
@Mapper
public interface BookMapper {

    BookMapper INSTANCE = Mappers.getMapper(BookMapper.class);

    // --- Entity <-> DTO ---

    /**
     * Maps a {@link Book} entity to a {@link BookResponse} DTO.
     *
     * @param book The book entity
     * @return The mapped {@link BookResponse} DTO
     */
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
    Book mapToEntity(BookRequest request);

    // --- DTO <-> SObject (Salesforce Models) ---

    /**
     * Maps a {@link BookResponse} DTO to a Salesforce {@link BookSObject}.
     *
     * @param dto The book response DTO
     * @return The mapped {@link BookSObject}
     */
    @Mapping(target = "externalBookUuid", source = "uuid")
    @Mapping(target = "name", source = "title", defaultValue = "Untitled")
    @Mapping(target = "title", source = "title")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    BookSObject toBookSObject(BookResponse dto);

    /**
     * Maps a Salesforce {@link BookSObject} to a {@link BookResponse} DTO.
     *
     * @param sObject The book SObject from Salesforce
     * @return The mapped {@link BookResponse} DTO
     */
    @Mapping(target = "uuid", source = "externalBookUuid", qualifiedByName = "parseUUID")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "title", source = "title", defaultExpression = "java(sObject.getName())")
    @Mapping(target = "author", source = "author")
    @Mapping(target = "isbn", source = "isbn")
    @Mapping(target = "coverImageUrl", source = "coverImageUrl")
    BookResponse toBookResponse(BookSObject sObject);

    /**
     * Maps a list of {@link BookSObject} records to a list of {@link BookResponse} DTOs.
     *
     * @param sObjects List of Book SObjects
     * @return List of mapped {@link BookResponse} DTOs
     */
    List<BookResponse> toBookResponseList(List<BookSObject> sObjects);

    // --- Helper Mapping Methods ---

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
}
