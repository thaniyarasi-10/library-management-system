package com.kovanlabs.librarymanagement.dto;

public record MembershipApplicationResponse(
        String membershipUuid,
        Long membershipId,
        String agreementHtml) {
}
