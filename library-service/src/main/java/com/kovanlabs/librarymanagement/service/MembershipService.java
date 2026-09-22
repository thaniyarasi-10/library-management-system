package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.MembershipApplicationResponse;
import com.kovanlabs.librarymanagement.dto.MembershipResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Service interface for library membership applications, digital signing, template fetching, and PDF rendering.
 */
public interface MembershipService {

    /**
     * Creates a pending membership application for a user by email and returns agreement HTML.
     *
     * @param email The user's email address
     * @return {@link MembershipApplicationResponse} containing membership UUID and filled HTML
     */
    MembershipApplicationResponse applyForMembership(String email);

    /**
     * Attaches a PNG signature to the agreement HTML, generates a signed PDF in S3, and activates the membership.
     *
     * @param membershipUuid Unique membership application UUID
     * @param file Multipart PNG signature file
     * @param email The applicant's email address
     * @return The updated {@link MembershipResponseDto}
     */
    MembershipResponseDto signAgreement(UUID membershipUuid, MultipartFile file, String email);

    /**
     * Retrieves the current membership status and details for the given user email.
     *
     * @param email The user's email address
     * @return The active or latest {@link MembershipResponseDto}
     */
    MembershipResponseDto getMyMembership(String email);

    /**
     * Cancels an active or pending membership application.
     *
     * @param email The user's email address
     * @return The updated cancelled {@link MembershipResponseDto}
     */
    MembershipResponseDto cancelMembership(String email);

    /**
     * Retrieves the filled agreement HTML for preview/review.
     *
     * @param membershipUuid Unique membership UUID
     * @param email The user's email address
     * @return The filled agreement HTML string
     */
    String getAgreementHtmlByUuid(UUID membershipUuid, String email);

    /**
     * Downloads the final signed agreement PDF from AWS S3.
     *
     * @param membershipId The numeric membership ID
     * @param email The user's email address
     * @return Byte array of the PDF document
     */
    byte[] downloadAgreementPdf(Long membershipId, String email);

    /**
     * Checks if the user with the specified UUID currently holds an active, unexpired membership.
     *
     * @param userUuid The user's unique UUID
     * @return {@code true} if an active, valid membership exists, {@code false} otherwise
     */
    boolean hasActiveMembership(UUID userUuid);
}
