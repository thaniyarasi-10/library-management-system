package com.kovanlabs.librarymanagement.membership.service;

import com.kovanlabs.librarymanagement.aws.s3.service.S3Service;
import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.MembershipStatus;
import com.kovanlabs.librarymanagement.database.repository.MembershipRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.membership.dto.MembershipApplicationResponse;
import com.kovanlabs.librarymanagement.membership.dto.MembershipResponseDto;
import com.kovanlabs.librarymanagement.membership.mapping.MembershipMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class MembershipServiceImpl implements MembershipService {

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final S3Service s3Service;
    private final MembershipMapper membershipMapper;

    @Value("${aws.s3.membership.bucket-name}")
    private String membershipBucketName;

    @Value("${aws.s3.membership.region}")
    private String membershipBucketRegion;

    @Value("${aws.s3.membership.template-key}")
    private String membershipTemplateKey;

    @Cacheable(value = "membership-agreement-template", key = "'template'")
    public String getAgreementTemplate() {
        log.info("Fetching agreement template from S3 bucket: {}, key: {}", membershipBucketName, membershipTemplateKey);
        return s3Service.downloadFileAsString(membershipBucketName, membershipBucketRegion, membershipTemplateKey);
    }

    @Override
    @Transactional
    public MembershipApplicationResponse applyForMembership(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        boolean exists = membershipRepository.existsByUserUuidAndStatusIn(
                user.getUuid(),
                Arrays.asList(MembershipStatus.PENDING, MembershipStatus.ACTIVE));

        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "User already has a pending or active membership");
        }

        Membership membership = Membership.builder()
                .membershipId(null)
                .userUuid(user.getUuid())
                .status(MembershipStatus.PENDING)
                .signed(false)
                .build();

        Membership saved = membershipRepository.save(membership);
        log.info("Membership application created for user: {} with UUID: {}", email, saved.getUuid());

        // Fetch agreement template using Redis/Spring Cache
        String agreementHtml = getAgreementTemplate();

        // Fill basic member details into the S3 template
        agreementHtml = agreementHtml
                .replace("{{MEMBER_NAME}}", user.getName())
                .replace("{{memberName}}", user.getName())
                .replace("{{MEMBER_EMAIL}}", user.getEmail())
                .replace("{{memberEmail}}", user.getEmail())
                .replace("{{MEMBERSHIP_ID}}", "PENDING")
                .replace("{{membershipId}}", "PENDING")
                .replace("{{START_DATE}}", LocalDate.now().toString())
                .replace("{{applicationDate}}", LocalDate.now().toString())
                .replace("{{EXPIRY_DATE}}", "N/A (Pending Activation)");

        return new MembershipApplicationResponse(saved.getUuid(), saved.getMembershipId(), agreementHtml);
    }

    @Override
    @Transactional
    public MembershipResponseDto signAgreement(UUID membershipUuid, MultipartFile file, String email) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature file is empty");
        }

        if (!"image/png".equalsIgnoreCase(file.getContentType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature must be a PNG image");
        }

        if (file.getSize() > 50 * 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signature file size must not exceed 50KB");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        Membership membership = membershipRepository.findByUuid(membershipUuid)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership application not found"));

        if (!membership.getUserUuid().equals(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access Denied: This application does not belong to you");
        }

        if (membership.getStatus() == MembershipStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Membership is already active");
        }

        try {
            Long newMembershipId = generateUniqueMembershipId();
            membership.setMembershipId(newMembershipId);

            // Fetch template using Redis cache
            String templateHtml = getAgreementTemplate();

            String base64Signature = Base64.getEncoder().encodeToString(file.getBytes());
            membership.setSignatureBase64(base64Signature);

            String signatureHtml = "<img class=\"signature-img\" style=\"max-width: 180px; max-height: 70px; object-fit: contain; display: block; margin-bottom: 5px;\" src=\"data:image/png;base64," + base64Signature + "\" />";

            String currentDate = LocalDate.now().toString();

            String filledHtml = templateHtml
                    .replace("{{MEMBER_NAME}}", user.getName())
                    .replace("{{memberName}}", user.getName())
                    .replace("{{MEMBER_EMAIL}}", user.getEmail())
                    .replace("{{memberEmail}}", user.getEmail())
                    .replace("{{MEMBERSHIP_ID}}", newMembershipId.toString())
                    .replace("{{membershipId}}", newMembershipId.toString())
                    .replace("{{START_DATE}}", membership.getCreatedAt().toLocalDate().toString())
                    .replace("{{applicationDate}}", membership.getCreatedAt().toLocalDate().toString())
                    .replace("{{EXPIRY_DATE}}", LocalDate.now().plusYears(1).toString())
                    .replace("{{SIGNED_DATE}}", currentDate)
                    .replace("{{APPROVAL_DATE}}", currentDate)
                    .replace("{{signaturePlaceholder}}", signatureHtml)
                    .replace("<div class=\"signature-placeholder\">\n            Signature\n        </div>",
                            signatureHtml)
                    .replace("<div class=\"signature-placeholder\">\r\n            Signature\r\n        </div>",
                            signatureHtml);

            byte[] pdfBytes = renderHtmlToPdf(filledHtml);

            String pdfKey = "signed-agreements/" + newMembershipId + "-signed-agreement.pdf";
            s3Service.uploadFileBytes(membershipBucketName, membershipBucketRegion, pdfKey, pdfBytes,
                    "application/pdf");

            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setSigned(true);
            membership.setSignedAt(LocalDateTime.now());
            membership.setActivatedAt(LocalDateTime.now());
            membership.setExpiryDate(LocalDate.now().plusYears(1));
            membership.setSignedPdfKey(pdfKey);

            Membership updated = membershipRepository.save(membership);
            log.info("Membership activated successfully for user: {} with ID: {}", email, updated.getMembershipId());

            evictActiveMembershipCache(user.getUuid());

            return membershipMapper.mapToResponse(updated);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process and sign agreement for user: {}", email, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to generate signed agreement PDF", e);
        }
    }

    @Override
    public MembershipResponseDto getMyMembership(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        Membership membership = membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(
                user.getUuid(),
                Arrays.asList(MembershipStatus.PENDING, MembershipStatus.ACTIVE, MembershipStatus.EXPIRED))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active, pending, or expired membership application found"));

        return membershipMapper.mapToResponse(membership);
    }

    @Override
    @Transactional
    public MembershipResponseDto cancelMembership(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        Membership membership = membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(
                user.getUuid(),
                Arrays.asList(MembershipStatus.PENDING, MembershipStatus.ACTIVE))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No active or pending membership application found to cancel"));

        if (membership.getStatus() == MembershipStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Membership is already cancelled");
        }

        membership.setStatus(MembershipStatus.CANCELLED);
        membership.setCancelledAt(LocalDateTime.now());
        Membership updated = membershipRepository.save(membership);

        evictActiveMembershipCache(user.getUuid());
        log.info("Membership cancelled successfully for user: {}", email);

        return membershipMapper.mapToResponse(updated);
    }

    @Override
    public String getAgreementHtmlByUuid(UUID membershipUuid, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        Membership membership = membershipRepository.findByUuid(membershipUuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));

        if (!membership.getUserUuid().equals(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        String html = getAgreementTemplate();
        log.info("Fetched template from S3");
        String membershipIdText = membership.getMembershipId() != null ? membership.getMembershipId().toString()
                : "PENDING";
        String startDateText = membership.getCreatedAt() != null ? membership.getCreatedAt().toLocalDate().toString()
                : LocalDate.now().toString();

        return html
                .replace("{{MEMBER_NAME}}", user.getName())
                .replace("{{memberName}}", user.getName())
                .replace("{{MEMBER_EMAIL}}", user.getEmail())
                .replace("{{memberEmail}}", user.getEmail())
                .replace("{{MEMBERSHIP_ID}}", membershipIdText)
                .replace("{{membershipId}}", membershipIdText)
                .replace("{{START_DATE}}", startDateText)
                .replace("{{applicationDate}}", startDateText)
                .replace("{{EXPIRY_DATE}}", "N/A (Pending Activation)")
                .replace("{{SIGNED_DATE}}", "Pending")
                .replace("{{APPROVAL_DATE}}", "Pending");
    }

    @Override
    public byte[] downloadAgreementPdf(Long membershipId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        Membership membership = membershipRepository.findByMembershipId(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));

        if (!membership.getUserUuid().equals(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        if (membership.getStatus() != MembershipStatus.ACTIVE || membership.getSignedPdfKey() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Signed PDF is not available yet");
        }

        return s3Service.downloadFile(membershipBucketName, membershipBucketRegion, membership.getSignedPdfKey());
    }

    @Override
    public boolean hasActiveMembership(UUID userUuid) {
        log.info("Checking active membership for user: {} in DATABASE", userUuid);
        Optional<Membership> membershipOpt = membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(
                userUuid, java.util.List.of(MembershipStatus.ACTIVE));
        if (membershipOpt.isEmpty()) {
            return false;
        }
        Membership membership = membershipOpt.get();
        boolean isNotExpired = membership.getExpiryDate() == null
                || !membership.getExpiryDate().isBefore(LocalDate.now());
        return isNotExpired;
    }

    @CacheEvict(value = "active-memberships", key = "#userUuid")
    public void evictActiveMembershipCache(UUID userUuid) {
        log.info("Evicting active membership cache for user: {}", userUuid);
    }

    private Long generateUniqueMembershipId() {
        long randomId;
        boolean unique;
        do {
            randomId = ThreadLocalRandom.current().nextLong(100000L, 1000000L);
            unique = membershipRepository.findByMembershipId(randomId).isEmpty();
        } while (!unique);
        return randomId;
    }

    private byte[] renderHtmlToPdf(String htmlContent) throws Exception {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, "/");
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        }
    }
}
