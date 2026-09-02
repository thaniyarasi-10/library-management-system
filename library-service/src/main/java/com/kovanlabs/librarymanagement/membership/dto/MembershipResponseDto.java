package com.kovanlabs.librarymanagement.membership.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record MembershipResponseDto(
    UUID uuid,
    Long membershipId,
    UUID userUuid,
    String status,
    LocalDateTime activatedAt,
    LocalDate expiryDate,
    boolean isSigned,
    LocalDateTime signedAt,
    String signedPdfKey,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
