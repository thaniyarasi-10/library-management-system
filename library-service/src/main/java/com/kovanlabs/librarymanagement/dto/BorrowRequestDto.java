package com.kovanlabs.librarymanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BorrowRequestDto(
        Long bookId,
        Long userId,
        String bookUuid,
        String userUuid
) {
    public BorrowRequestDto(Long bookId, Long userId) {
        this(bookId, userId, null, null);
    }

    public BorrowRequestDto(String bookUuid, String userUuid) {
        this(null, null, bookUuid, userUuid);
    }
}
