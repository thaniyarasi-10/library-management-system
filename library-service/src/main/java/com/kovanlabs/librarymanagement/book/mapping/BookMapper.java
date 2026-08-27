package com.kovanlabs.librarymanagement.book.mapping;

import com.kovanlabs.librarymanagement.book.dto.BookRequest;
import com.kovanlabs.librarymanagement.book.dto.BookResponse;
import com.kovanlabs.librarymanagement.book.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.book.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.util.List;

@Mapper(componentModel = "spring", imports = {LocalDate.class, BorrowStatus.class})
public interface BookMapper {

    BookResponse mapToResponse(Book book);

    List<BookResponse> mapToResponse(List<Book> books);

    @Mapping(target = "borrowUuid", source = "uuid")
    @Mapping(target = "bookId", source = "book.uuid")
    @Mapping(target = "userId", source = "user.uuid")
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
}
