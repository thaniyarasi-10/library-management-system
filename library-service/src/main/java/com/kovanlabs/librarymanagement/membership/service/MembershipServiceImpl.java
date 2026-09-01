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

    @Override
    @Transactional
    public MembershipApplicationResponse applyForMembership(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        boolean exists = membershipRepository.existsByUserUuidAndStatusIn(
                user.getUuid(),
                Arrays.asList(MembershipStatus.PENDING, MembershipStatus.ACTIVE)
        );

        if (exists) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User already has a pending or active membership");
        }

        Long membershipId = generateUniqueMembershipId();

        Membership membership = Membership.builder()
                .membershipId(membershipId)
                .userUuid(user.getUuid())
                .status(MembershipStatus.PENDING)
                .signed(false)
                .build();

        Membership saved = membershipRepository.save(membership);
        log.info("Membership application created for user: {} with ID: {}", email, saved.getMembershipId());

        String agreementHtml;
        try {
            agreementHtml = s3Service.downloadFileAsString(membershipBucketName, membershipBucketRegion, membershipTemplateKey);
        } catch (Exception e) {
            log.warn("Failed to download template from S3: {}, using default fallback agreement template", e.getMessage());
            agreementHtml = getDefaultAgreementTemplate();
        }

        // Fill basic details
        agreementHtml = agreementHtml
                .replace("{{memberName}}", user.getName())
                .replace("{{memberEmail}}", user.getEmail())
                .replace("{{membershipId}}", membershipId.toString())
                .replace("{{applicationDate}}", LocalDate.now().toString());

        return new MembershipApplicationResponse(saved.getUuid(), saved.getMembershipId(), agreementHtml);
    }

    @Override
    @Transactional
    @CacheEvict(value = "active-memberships", key = "#p2")
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership application not found"));

        if (!membership.getUserUuid().equals(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: This application does not belong to you");
        }

        if (membership.getStatus() == MembershipStatus.ACTIVE || membership.isSigned()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Membership is already active");
        }

        try {
            String templateHtml;
            try {
                templateHtml = s3Service.downloadFileAsString(membershipBucketName, membershipBucketRegion, membershipTemplateKey);
            } catch (Exception e) {
                log.warn("Failed to download template from S3: {}, using default fallback agreement template", e.getMessage());
                templateHtml = getDefaultAgreementTemplate();
            }

            String base64Signature = Base64.getEncoder().encodeToString(file.getBytes());
            String signatureHtml = "<img class=\"signature-img\" src=\"data:image/png;base64," + base64Signature + "\" />";

            String filledHtml = templateHtml
                    .replace("{{memberName}}", user.getName())
                    .replace("{{memberEmail}}", user.getEmail())
                    .replace("{{membershipId}}", membership.getMembershipId().toString())
                    .replace("{{applicationDate}}", membership.getCreatedAt().toLocalDate().toString())
                    .replace("{{signaturePlaceholder}}", signatureHtml);

            byte[] pdfBytes = renderHtmlToPdf(filledHtml);

            String pdfKey = "signed-agreements/" + membership.getMembershipId() + "-signed-agreement.pdf";
            s3Service.uploadFileBytes(membershipBucketName, membershipBucketRegion, pdfKey, pdfBytes, "application/pdf");

            membership.setStatus(MembershipStatus.ACTIVE);
            membership.setSigned(true);
            membership.setSignedAt(LocalDateTime.now());
            membership.setActivatedAt(LocalDateTime.now());
            membership.setExpiryDate(LocalDate.now().plusYears(1));
            membership.setSignedPdfKey(pdfKey);

            Membership updated = membershipRepository.save(membership);
            log.info("Membership activated successfully for user: {} with ID: {}", email, updated.getMembershipId());

            // Evict cache by user UUID as well, since the cacheable method uses userUuid
            evictActiveMembershipCache(user.getUuid());

            return membershipMapper.mapToResponse(updated);

        } catch (Exception e) {
            log.error("Failed to process and sign agreement for user: {}", email, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to generate signed agreement PDF", e);
        }
    }

    @Override
    public MembershipResponseDto getMyMembership(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        Membership membership = membershipRepository.findByUserUuid(user.getUuid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No membership application found"));

        return membershipMapper.mapToResponse(membership);
    }

    @Override
    public String getAgreementHtml(Long membershipId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + email));

        Membership membership = membershipRepository.findByMembershipId(membershipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membership not found"));

        if (!membership.getUserUuid().equals(user.getUuid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied");
        }

        try {
            return s3Service.downloadFileAsString(membershipBucketName, membershipBucketRegion, membershipTemplateKey);
        } catch (Exception e) {
            log.warn("Failed to download template from S3: {}, returning default fallback template", e.getMessage());
            return getDefaultAgreementTemplate();
        }
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
        Optional<Membership> membershipOpt = membershipRepository.findByUserUuid(userUuid);
        if (membershipOpt.isEmpty()) {
            return false;
        }
        Membership membership = membershipOpt.get();
        boolean isActive = membership.getStatus() == MembershipStatus.ACTIVE;
        boolean isNotExpired = membership.getExpiryDate() == null || !membership.getExpiryDate().isBefore(LocalDate.now());
        return isActive && isNotExpired;
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

    private String getDefaultAgreementTemplate() {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\"/>\n" +
                "    <title>Library Membership Agreement</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: Arial, sans-serif;\n" +
                "            margin: 40px;\n" +
                "            color: #333333;\n" +
                "            line-height: 1.6;\n" +
                "        }\n" +
                "        .header {\n" +
                "            text-align: center;\n" +
                "            margin-bottom: 30px;\n" +
                "            border-bottom: 2px solid #2C3E50;\n" +
                "            padding-bottom: 10px;\n" +
                "        }\n" +
                "        .title {\n" +
                "            font-size: 24px;\n" +
                "            color: #2C3E50;\n" +
                "            font-weight: bold;\n" +
                "            text-transform: uppercase;\n" +
                "        }\n" +
                "        .section {\n" +
                "            margin-bottom: 20px;\n" +
                "        }\n" +
                "        .section-title {\n" +
                "            font-size: 18px;\n" +
                "            color: #2C3E50;\n" +
                "            font-weight: bold;\n" +
                "            margin-bottom: 10px;\n" +
                "        }\n" +
                "        .details-table {\n" +
                "            width: 100%;\n" +
                "            border-collapse: collapse;\n" +
                "            margin: 20px 0;\n" +
                "        }\n" +
                "        .details-table td {\n" +
                "            padding: 10px;\n" +
                "            border: 1px solid #BDC3C7;\n" +
                "        }\n" +
                "        .details-table td.label {\n" +
                "            font-weight: bold;\n" +
                "            background-color: #ECF0F1;\n" +
                "            width: 30%;\n" +
                "        }\n" +
                "        .signature-container {\n" +
                "            margin-top: 40px;\n" +
                "        }\n" +
                "        .signature-box {\n" +
                "            border: 1px dashed #7F8C8D;\n" +
                "            height: 100px;\n" +
                "            width: 250px;\n" +
                "            margin-top: 10px;\n" +
                "            text-align: center;\n" +
                "            line-height: 100px;\n" +
                "            color: #7F8C8D;\n" +
                "        }\n" +
                "        .signature-img {\n" +
                "            max-height: 90px;\n" +
                "            max-width: 240px;\n" +
                "            vertical-align: middle;\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"header\">\n" +
                "        <div class=\"title\">Library Membership Agreement</div>\n" +
                "    </div>\n" +
                "    \n" +
                "    <div class=\"section\">\n" +
                "        <p>This agreement outlines the borrowing regulations and code of conduct for library members.</p>\n" +
                "        <table class=\"details-table\">\n" +
                "            <tr>\n" +
                "                <td class=\"label\">Member Name</td>\n" +
                "                <td>{{memberName}}</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "                <td class=\"label\">Member Email</td>\n" +
                "                <td>{{memberEmail}}</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "                <td class=\"label\">Membership ID</td>\n" +
                "                <td>{{membershipId}}</td>\n" +
                "            </tr>\n" +
                "            <tr>\n" +
                "                <td class=\"label\">Application Date</td>\n" +
                "                <td>{{applicationDate}}</td>\n" +
                "            </tr>\n" +
                "        </table>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"section\">\n" +
                "        <div class=\"section-title\">Terms of Service</div>\n" +
                "        <p>1. Members are responsible for all library materials checked out on their account.</p>\n" +
                "        <p>2. Late returns, lost items, and damaged items are subject to fines as per the library policy.</p>\n" +
                "        <p>3. Borrowing privileges will be suspended if fines exceed threshold limits or memberships expire.</p>\n" +
                "    </div>\n" +
                "\n" +
                "    <div class=\"signature-container\">\n" +
                "        <p>By providing your signature below, you confirm that you accept all rules, regulations, and terms of service of the library.</p>\n" +
                "        <div class=\"signature-box\">\n" +
                "            {{signaturePlaceholder}}\n" +
                "        </div>\n" +
                "    </div>\n" +
                "</body>\n" +
                "</html>";
    }
}
