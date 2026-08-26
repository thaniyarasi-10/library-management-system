package com.kovanlabs.librarymanagement.fine.mapping;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.fine.dto.FineResponseDto;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class FineMapping {

    public static FineResponseDto mapToResponse(Fine fine) {
        if (fine == null) {
            return null;
        }
        return FineResponseDto.builder()
                .uuid(fine.getUuid())
                .id(fine.getId())
                .bookUuid(fine.getBookUuid())
                .userUuid(fine.getUserUuid())
                .pendingFineAmount(fine.getPendingFineAmount())
                .status(fine.getStatus())
                .createdAt(fine.getCreatedAt())
                .updatedAt(fine.getUpdatedAt())
                .build();
    }

    public static List<FineResponseDto> mapToResponse(List<Fine> fines) {
        if (fines == null) {
            return Collections.emptyList();
        }
        return fines.stream()
                .map(FineMapping::mapToResponse)
                .collect(Collectors.toList());
    }
}
