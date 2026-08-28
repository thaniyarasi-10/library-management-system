package com.kovanlabs.librarymanagement.fine.mapping;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.database.enums.FineStatus;
import com.kovanlabs.librarymanagement.fine.dto.FineResponseDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FineMappingTest {

    @Test
    void testMapToResponse_SingleFine() {
        UUID fineUuid = UUID.randomUUID();
        UUID bookUuid = UUID.randomUUID();
        UUID userUuid = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Fine fine = Fine.builder()
                .uuid(fineUuid)
                .id(15L)
                .bookUuid(bookUuid)
                .userUuid(userUuid)
                .pendingFineAmount(BigDecimal.valueOf(25.50))
                .status(FineStatus.PENDING)
                .createdAt(now)
                .updatedAt(now)
                .build();

        FineResponseDto response = FineMapping.mapToResponse(fine);

        assertNotNull(response);
        assertEquals(fineUuid, response.uuid());
        assertEquals(15L, response.id());
        assertEquals(bookUuid, response.bookUuid());
        assertEquals(userUuid, response.userUuid());
        assertEquals(BigDecimal.valueOf(25.50), response.pendingFineAmount());
        assertEquals(FineStatus.PENDING, response.status());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void testMapToResponse_NullFine() {
        assertNull(FineMapping.mapToResponse((Fine) null));
    }

    @Test
    void testMapToResponse_FineList() {
        Fine fine1 = Fine.builder().id(1L).pendingFineAmount(BigDecimal.TEN).status(FineStatus.PENDING).build();
        Fine fine2 = Fine.builder().id(2L).pendingFineAmount(BigDecimal.ZERO).status(FineStatus.PAID).build();

        List<FineResponseDto> responses = FineMapping.mapToResponse(List.of(fine1, fine2));

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).id());
        assertEquals(FineStatus.PENDING, responses.get(0).status());
        assertEquals(2L, responses.get(1).id());
        assertEquals(FineStatus.PAID, responses.get(1).status());
    }

    @Test
    void testMapToResponse_NullFineList() {
        List<FineResponseDto> responses = FineMapping.mapToResponse((List<Fine>) null);
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }
}
