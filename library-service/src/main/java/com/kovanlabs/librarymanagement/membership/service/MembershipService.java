package com.kovanlabs.librarymanagement.membership.service;

import com.kovanlabs.librarymanagement.membership.dto.MembershipApplicationResponse;
import com.kovanlabs.librarymanagement.membership.dto.MembershipResponseDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MembershipService {
    MembershipApplicationResponse applyForMembership(String email);
    MembershipResponseDto signAgreement(UUID membershipUuid, MultipartFile file, String email);
    MembershipResponseDto getMyMembership(String email);
    MembershipResponseDto cancelMembership(String email);
    String getAgreementHtmlByUuid(UUID membershipUuid, String email);
    byte[] downloadAgreementPdf(Long membershipId, String email);
    boolean hasActiveMembership(UUID userUuid);
}
