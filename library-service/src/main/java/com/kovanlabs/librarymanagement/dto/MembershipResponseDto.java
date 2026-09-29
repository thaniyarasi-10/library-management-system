package com.kovanlabs.librarymanagement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record MembershipResponseDto(
    String uuid,
    Long id,
    Long membershipId,
    String userUuid,
    String status,
    LocalDateTime activatedAt,
    LocalDate expiryDate,
    boolean isSigned,
    LocalDateTime signedAt,
    String signedPdfKey,
    String signatureBase64,
    LocalDateTime cancelledAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
