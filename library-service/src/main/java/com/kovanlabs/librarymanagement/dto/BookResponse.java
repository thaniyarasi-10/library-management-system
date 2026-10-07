package com.kovanlabs.librarymanagement.dto;

import java.io.Serializable;

public record BookResponse(
        String uuid,
        Long id,
        String title,
        String author,
        String isbn,
        String coverImageUrl,
        Integer totalBookCount,
        Integer borrowedBookCount,
        Integer availableBookCount
) implements Serializable {
    public BookResponse(
            String uuid,
            Long id,
            String title,
            String author,
            String isbn,
            String coverImageUrl,
            Integer totalBookCount,
            Integer borrowedBookCount
    ) {
        this(
                uuid,
                id,
                title,
                author,
                isbn,
                coverImageUrl,
                totalBookCount,
                borrowedBookCount,
                (totalBookCount != null ? totalBookCount : 0) - (borrowedBookCount != null ? borrowedBookCount : 0)
        );
    }
}

