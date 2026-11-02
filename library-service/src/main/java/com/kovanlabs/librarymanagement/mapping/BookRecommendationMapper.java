package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.dto.BookAvailabilityStatus;
import com.kovanlabs.librarymanagement.dto.RecommendedBookItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Objects;

/**
 * MapStruct mapper for converting catalog Book entities to RecommendedBookItem DTOs.
 */
@Mapper(imports = {Objects.class})
public interface BookRecommendationMapper {

    BookRecommendationMapper INSTANCE = Mappers.getMapper(BookRecommendationMapper.class);

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "bookUuid", source = "book.uuid")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "author", source = "book.author")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "coverImageUrl", source = "book.coverImageUrl")
    @Mapping(target = "category", source = "book.category")
    @Mapping(target = "explanation", source = "explanation")
    @Mapping(target = "availabilityStatus", source = "status")
    @Mapping(target = "availableCopies", source = "availableCopies")
    @Mapping(target = "totalCopies", expression = "java(book != null && book.getTotalBookCount() != null ? book.getTotalBookCount() : 0)")
    @Mapping(target = "borrowedCopies", expression = "java(book != null && book.getBorrowedBookCount() != null ? book.getBorrowedBookCount() : 0)")
    RecommendedBookItem toRecommendedBookItem(Book book, String explanation, BookAvailabilityStatus status, Integer availableCopies);
}
