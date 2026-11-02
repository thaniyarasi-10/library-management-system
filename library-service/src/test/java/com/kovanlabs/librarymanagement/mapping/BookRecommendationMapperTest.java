package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import com.kovanlabs.librarymanagement.dto.BookAvailabilityStatus;
import com.kovanlabs.librarymanagement.dto.RecommendedBookItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BookRecommendationMapperTest {

    @Test
    @DisplayName("Maps Book entity to RecommendedBookItem correctly")
    void testToRecommendedBookItem() {
        String uuid = UUID.randomUUID().toString();
        Book book = Book.builder()
                .uuid(uuid)
                .id(42L)
                .title("Clean Architecture")
                .author("Robert C. Martin")
                .isbn("9780134494166")
                .coverImageUrl("https://covers.com/clean.jpg")
                .category(BookCategory.TECHNOLOGY)
                .totalBookCount(5)
                .borrowedBookCount(2)
                .build();

        RecommendedBookItem item = BookRecommendationMapper.INSTANCE.toRecommendedBookItem(
                book,
                "Outstanding guide to software architecture.",
                BookAvailabilityStatus.AVAILABLE,
                3
        );

        assertNotNull(item);
        assertEquals(42L, item.bookId());
        assertEquals(uuid, item.bookUuid());
        assertEquals("Clean Architecture", item.title());
        assertEquals("Robert C. Martin", item.author());
        assertEquals("9780134494166", item.isbn());
        assertEquals("https://covers.com/clean.jpg", item.coverImageUrl());
        assertEquals(BookCategory.TECHNOLOGY, item.category());
        assertEquals("Outstanding guide to software architecture.", item.explanation());
        assertEquals(BookAvailabilityStatus.AVAILABLE, item.availabilityStatus());
        assertEquals(3, item.availableCopies());
        assertEquals(5, item.totalCopies());
        assertEquals(2, item.borrowedCopies());
    }

    @Test
    @DisplayName("Maps Book entity with null counts defaults to 0")
    void testToRecommendedBookItem_NullCounts() {
        Book book = Book.builder()
                .uuid("test-uuid")
                .id(1L)
                .title("Book Title")
                .author("Author")
                .totalBookCount(null)
                .borrowedBookCount(null)
                .build();

        RecommendedBookItem item = BookRecommendationMapper.INSTANCE.toRecommendedBookItem(
                book,
                "Explanation",
                BookAvailabilityStatus.UNAVAILABLE,
                0
        );

        assertNotNull(item);
        assertEquals(0, item.totalCopies());
        assertEquals(0, item.borrowedCopies());
    }

    @Test
    @DisplayName("Maps null book returns item with explanation and status")
    void testToRecommendedBookItem_NullBook() {
        RecommendedBookItem item = BookRecommendationMapper.INSTANCE.toRecommendedBookItem(
                null,
                "Explanation",
                BookAvailabilityStatus.NOT_IN_CATALOG,
                0
        );
        assertNotNull(item);
        assertNull(item.title());
        assertNull(item.bookId());
        assertEquals("Explanation", item.explanation());
        assertEquals(BookAvailabilityStatus.NOT_IN_CATALOG, item.availabilityStatus());
    }
}
