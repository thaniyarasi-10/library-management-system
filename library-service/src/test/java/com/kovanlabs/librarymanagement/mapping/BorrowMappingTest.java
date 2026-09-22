package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.Borrow;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.BorrowStatus;
import com.kovanlabs.librarymanagement.dto.BorrowRequestDto;
import com.kovanlabs.librarymanagement.dto.BorrowResponseDto;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BookSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.BorrowSObject;
import com.kovanlabs.librarymanagement.salesforce.model.sobjects.ContactSObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BorrowMappingTest {

    private final BorrowMapper borrowMapper = BorrowMapper.INSTANCE;

    @Test
    void testMapToResponse_SingleBorrow() {
        UUID borrowUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();

        Book book = Book.builder()
                .uuid(bookUuid)
                .id(10L)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .coverImageUrl("http://img.png")
                .build();

        User user = User.builder()
                .uuid(userUuid)
                .id(20L)
                .name("John Doe")
                .email("john@example.com")
                .build();

        Borrow borrow = Borrow.builder()
                .uuid(borrowUuid)
                .id(5L)
                .book(book)
                .user(user)
                .borrowDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();

        BorrowResponseDto response = borrowMapper.mapToResponse(borrow);

        assertNotNull(response);
        assertEquals(borrowUuid, response.borrowUuid());
        assertEquals(5L, response.id());
        assertEquals(bookUuid, response.bookId());
        assertEquals(10L, response.bookNumericId());
        assertEquals("Clean Architecture", response.bookTitle());
        assertEquals("Robert C. Martin", response.bookAuthor());
        assertEquals("http://img.png", response.bookCoverImageUrl());
        assertEquals(userUuid, response.userId());
        assertEquals(20L, response.userNumericId());
        assertEquals("John Doe", response.userName());
        assertEquals("john@example.com", response.userEmail());
        assertEquals(BorrowStatus.BORROWED, response.status());
    }

    @Test
    void testMapToResponse_NullBorrow() {
        assertNull(borrowMapper.mapToResponse((Borrow) null));
    }

    @Test
    void testMapToResponse_BorrowList() {
        Borrow b1 = Borrow.builder().id(1L).status(BorrowStatus.BORROWED).build();
        Borrow b2 = Borrow.builder().id(2L).status(BorrowStatus.RETURNED).build();

        List<BorrowResponseDto> responses = borrowMapper.mapToResponseForBorrows(List.of(b1, b2));

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(BorrowStatus.BORROWED, responses.get(0).status());
        assertEquals(BorrowStatus.RETURNED, responses.get(1).status());
    }

    @Test
    void testMapToResponse_NullBorrowList() {
        List<BorrowResponseDto> responses = borrowMapper.mapToResponseForBorrows(null);
        assertNull(responses);
    }

    @Test
    void testMapToEntity_BorrowRequest() {
        BorrowRequestDto request = new BorrowRequestDto(100L, 200L);
        Book book = Book.builder().id(100L).build();
        User user = User.builder().id(200L).build();

        Borrow borrow = borrowMapper.mapToEntity(request, book, user);

        assertNotNull(borrow);
        assertEquals(book, borrow.getBook());
        assertEquals(user, borrow.getUser());
        assertEquals(BorrowStatus.BORROWED, borrow.getStatus());
        assertEquals(LocalDate.now(), borrow.getBorrowDate());
        assertEquals(LocalDate.now().plusDays(14), borrow.getDueDate());
    }

    @Test
    void testToBorrowSObject_and_toBorrowResponse() {
        UUID borrowUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        LocalDate now = LocalDate.now();

        BorrowResponseDto dto = BorrowResponseDto.builder()
                .borrowUuid(borrowUuid)
                .userId(userUuid)
                .userNumericId(99L)
                .userName("Alice")
                .userEmail("alice@example.com")
                .bookId(bookUuid)
                .bookTitle("DDD")
                .bookAuthor("Eric Evans")
                .bookCoverImageUrl("http://cover.jpg")
                .borrowDate(now)
                .dueDate(now.plusDays(14))
                .status(BorrowStatus.BORROWED)
                .build();

        BorrowSObject sObject = borrowMapper.toBorrowSObject(dto);
        assertNotNull(sObject);
        assertEquals(borrowUuid.toString(), sObject.getExternalBorrowUuid());
        assertEquals("BORROWED", sObject.getBorrowStatus());
        assertNotNull(sObject.getContact());
        assertEquals(userUuid.toString(), sObject.getContact().getExternalUserUuid());
        assertEquals(99L, sObject.getContact().getLegacyUserId());
        assertNotNull(sObject.getBook());
        assertEquals(bookUuid.toString(), sObject.getBook().getExternalBookUuid());
        assertEquals("DDD", sObject.getBook().getTitle());

        BorrowResponseDto mappedBack = borrowMapper.toBorrowResponse(sObject);
        assertNotNull(mappedBack);
        assertEquals(borrowUuid, mappedBack.borrowUuid());
        assertEquals(userUuid, mappedBack.userId());
        assertEquals(99L, mappedBack.userNumericId());
        assertEquals("Alice", mappedBack.userName());
        assertEquals(bookUuid, mappedBack.bookId());
        assertEquals("DDD", mappedBack.bookTitle());
        assertEquals(BorrowStatus.BORROWED, mappedBack.status());
    }

    @Test
    void testToBorrowResponseList() {
        BorrowSObject s1 = BorrowSObject.builder()
                .externalBorrowUuid(UUID.randomUUID().toString())
                .borrowStatus("BORROWED")
                .build();
        BorrowSObject s2 = BorrowSObject.builder()
                .externalBorrowUuid(UUID.randomUUID().toString())
                .borrowStatus("RETURNED")
                .build();

        List<BorrowResponseDto> responses = borrowMapper.toBorrowResponseList(List.of(s1, s2));

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(BorrowStatus.BORROWED, responses.get(0).status());
        assertEquals(BorrowStatus.RETURNED, responses.get(1).status());
    }

    @Test
    void testToBorrowResponseList_Null() {
        assertNull(borrowMapper.toBorrowResponseList(null));
    }

    @Test
    void testHelperMethods() {
        assertNull(borrowMapper.parseUUID(null));
        assertNull(borrowMapper.parseUUID("invalid-uuid"));
        assertNotNull(borrowMapper.parseUUID(UUID.randomUUID().toString()));

        assertNull(borrowMapper.parseLocalDate(null));
        assertNull(borrowMapper.parseLocalDate("invalid-date"));
        assertEquals(LocalDate.of(2025, 1, 1), borrowMapper.parseLocalDate("2025-01-01"));

        assertNull(borrowMapper.parseBorrowStatus(null));
        assertNull(borrowMapper.parseBorrowStatus("INVALID_STATUS"));
        assertEquals(BorrowStatus.BORROWED, borrowMapper.parseBorrowStatus("BORROWED"));

        assertNull(borrowMapper.resolveBookTitle(null));
        BookSObject b1 = BookSObject.builder().title("Title 1").name("Name 1").build();
        assertEquals("Title 1", borrowMapper.resolveBookTitle(b1));
        BookSObject b2 = BookSObject.builder().title("").name("Name 2").build();
        assertEquals("Name 2", borrowMapper.resolveBookTitle(b2));
    }
}
