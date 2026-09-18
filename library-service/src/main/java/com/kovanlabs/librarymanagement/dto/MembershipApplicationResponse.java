package com.kovanlabs.librarymanagement.dto;

import java.util.UUID;

public record MembershipApplicationResponse(
        UUID membershipUuid,
        Long membershipId,
        String agreementHtml) {
}
