package com.kovanlabs.librarymanagement.dto;

import com.kovanlabs.librarymanagement.database.enums.DonationStatus;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record BookDonationResponse(
        Long id,
        Long userId,
        String userName,
        String userEmail,
        Long bookId,
        String title,
        String author,
        String isbn,
        String coverImageUrl,
        String coverImageKey,
        Integer donatedBookCount,
        DonationStatus status,
        LocalDateTime reviewedAt,
        String rejectionReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
