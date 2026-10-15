package com.kovanlabs.librarymanagement.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BorrowRequestDto(
        Long bookId,
        Long userId,
        String bookUuid,
        String userUuid
) {}
