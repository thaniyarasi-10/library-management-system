package com.kovanlabs.librarymanagement.dto;

import com.kovanlabs.librarymanagement.database.enums.BookCategory;
import java.io.Serializable;

/**
 * Details of an individual recommended book including verified library availability.
 */
public record RecommendedBookItem(
        String title,
        String author,
        String explanation,
        String isbn,
        BookAvailabilityStatus availabilityStatus,
        Integer availableCopies,
        Integer totalCopies,
        Integer borrowedCopies,
        String bookUuid,
        Long bookId,
        String coverImageUrl,
        BookCategory category
) implements Serializable {
}

