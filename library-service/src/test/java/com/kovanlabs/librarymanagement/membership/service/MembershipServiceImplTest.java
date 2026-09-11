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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipServiceImplTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private MembershipMapper membershipMapper;

    @InjectMocks
    private MembershipServiceImpl membershipService;

    private User user;
    private UUID userUuid;
    private final String email = "test@example.com";
    private final String sampleHtmlTemplate = "<html><body>Hello {{MEMBER_NAME}} {{MEMBERSHIP_ID}} {{START_DATE}} {{EXPIRY_DATE}} {{signaturePlaceholder}}</body></html>";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(membershipService, "membershipBucketName", "test-bucket");
        ReflectionTestUtils.setField(membershipService, "membershipBucketRegion", "us-east-1");
        ReflectionTestUtils.setField(membershipService, "membershipTemplateKey", "templates/agreement.html");

        userUuid = UUID.randomUUID();
        user = User.builder()
                .uuid(userUuid)
                .email(email)
                .name("John Doe")
                .build();
    }

    private MembershipResponseDto createSampleDto(UUID memUuid, String status) {
        return new MembershipResponseDto(
                memUuid, 123456L, userUuid, status,
                LocalDateTime.now(), LocalDate.now().plusYears(1), true,
                LocalDateTime.now(), "key.pdf", "sigBase64",
                null, LocalDateTime.now(), LocalDateTime.now()
        );
    }

    @Test
    void testGetAgreementTemplate() {
        when(s3Service.downloadFileAsString("test-bucket", "us-east-1", "templates/agreement.html"))
                .thenReturn(sampleHtmlTemplate);

        String template = membershipService.getAgreementTemplate();

        assertEquals(sampleHtmlTemplate, template);
        verify(s3Service).downloadFileAsString("test-bucket", "us-east-1", "templates/agreement.html");
    }

    @Test
    void testApplyForMembership_success() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserUuidAndStatusIn(eq(userUuid), any())).thenReturn(false);
        when(s3Service.downloadFileAsString(anyString(), anyString(), anyString())).thenReturn(sampleHtmlTemplate);

        UUID newMemUuid = UUID.randomUUID();
        Membership savedMembership = Membership.builder()
                .uuid(newMemUuid)
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .signed(false)
                .build();

        when(membershipRepository.save(any(Membership.class))).thenReturn(savedMembership);

        MembershipApplicationResponse response = membershipService.applyForMembership(email);

        assertNotNull(response);
        assertEquals(newMemUuid, response.membershipUuid());
        assertTrue(response.agreementHtml().contains("John Doe"));
        assertTrue(response.agreementHtml().contains("PENDING"));
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void testApplyForMembership_userNotFound_throwsNotFound() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> membershipService.applyForMembership(email));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void testApplyForMembership_alreadyActiveOrPending_throwsBadRequest() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserUuidAndStatusIn(eq(userUuid), any())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> membershipService.applyForMembership(email));
        verify(membershipRepository, never()).save(any());
    }

    @Test
    void testSignAgreement_emptyFile_throwsBadRequest() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "sig.png", "image/png", new byte[0]);
        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(UUID.randomUUID(), emptyFile, email));
    }

    @Test
    void testSignAgreement_invalidContentType_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.jpg", "image/jpeg", new byte[]{1, 2, 3});
        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(UUID.randomUUID(), file, email));
    }

    @Test
    void testSignAgreement_fileExceedsSize_throwsBadRequest() {
        byte[] largeBytes = new byte[51 * 1024];
        MockMultipartFile largeFile = new MockMultipartFile("file", "sig.png", "image/png", largeBytes);
        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(UUID.randomUUID(), largeFile, email));
    }

    @Test
    void testSignAgreement_userNotFound_throwsNotFound() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", new byte[]{1, 2, 3});
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(UUID.randomUUID(), file, email));
    }

    @Test
    void testSignAgreement_membershipNotFound_throwsNotFound() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", new byte[]{1, 2, 3});
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUuid(any())).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(UUID.randomUUID(), file, email));
    }

    @Test
    void testSignAgreement_forbiddenUser_throwsForbidden() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", new byte[]{1, 2, 3});
        Membership otherMembership = Membership.builder()
                .uuid(UUID.randomUUID())
                .userUuid(UUID.randomUUID()) // Different user
                .status(MembershipStatus.PENDING)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUuid(any())).thenReturn(Optional.of(otherMembership));

        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(UUID.randomUUID(), file, email));
    }

    @Test
    void testSignAgreement_alreadyActive_throwsBadRequest() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", new byte[]{1, 2, 3});
        Membership activeMembership = Membership.builder()
                .uuid(UUID.randomUUID())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUuid(any())).thenReturn(Optional.of(activeMembership));

        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(UUID.randomUUID(), file, email));
    }

    @Test
    void testSignAgreement_success() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", new byte[]{1, 2, 3});
        UUID memUuid = UUID.randomUUID();
        Membership pendingMembership = Membership.builder()
                .uuid(memUuid)
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUuid(memUuid)).thenReturn(Optional.of(pendingMembership));
        when(membershipRepository.findByMembershipId(anyLong())).thenReturn(Optional.empty());
        when(s3Service.downloadFileAsString(anyString(), anyString(), anyString())).thenReturn(sampleHtmlTemplate);
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MembershipResponseDto responseDto = createSampleDto(memUuid, "ACTIVE");
        when(membershipMapper.mapToResponse(any())).thenReturn(responseDto);

        MembershipResponseDto result = membershipService.signAgreement(memUuid, file, email);

        assertNotNull(result);
        assertEquals("ACTIVE", result.status());
        verify(s3Service).uploadFileBytes(eq("test-bucket"), eq("us-east-1"), anyString(), any(byte[].class), eq("application/pdf"));
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void testGetMyMembership_success() {
        Membership membership = Membership.builder()
                .uuid(UUID.randomUUID())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(membership));

        MembershipResponseDto responseDto = createSampleDto(membership.getUuid(), "ACTIVE");
        when(membershipMapper.mapToResponse(membership)).thenReturn(responseDto);

        MembershipResponseDto result = membershipService.getMyMembership(email);

        assertNotNull(result);
        assertEquals(membership.getUuid(), result.uuid());
    }

    @Test
    void testCancelMembership_success() {
        Membership membership = Membership.builder()
                .uuid(UUID.randomUUID())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MembershipResponseDto responseDto = createSampleDto(membership.getUuid(), "CANCELLED");
        when(membershipMapper.mapToResponse(any())).thenReturn(responseDto);

        MembershipResponseDto result = membershipService.cancelMembership(email);

        assertNotNull(result);
        assertEquals("CANCELLED", result.status());
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void testGetAgreementHtmlByUuid_success() {
        UUID memUuid = UUID.randomUUID();
        Membership membership = Membership.builder()
                .uuid(memUuid)
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findByUuid(memUuid)).thenReturn(Optional.of(membership));
        when(s3Service.downloadFileAsString(anyString(), anyString(), anyString())).thenReturn(sampleHtmlTemplate);

        String html = membershipService.getAgreementHtmlByUuid(memUuid, email);

        assertNotNull(html);
        assertTrue(html.contains("John Doe"));
    }

    @Test
    void testDownloadAgreementPdf_success() {
        Long memId = 999111L;
        Membership membership = Membership.builder()
                .uuid(UUID.randomUUID())
                .membershipId(memId)
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .signedPdfKey("signed-agreements/999111-signed-agreement.pdf")
                .build();

        byte[] pdfBytes = "SAMPLE-PDF".getBytes();

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(membershipRepository.findByMembershipId(memId)).thenReturn(Optional.of(membership));
        when(s3Service.downloadFile("test-bucket", "us-east-1", "signed-agreements/999111-signed-agreement.pdf")).thenReturn(pdfBytes);

        byte[] result = membershipService.downloadAgreementPdf(memId, email);

        assertArrayEquals(pdfBytes, result);
    }

    @Test
    void testHasActiveMembership_trueAndFalse() {
        Membership activeMembership = Membership.builder()
                .uuid(UUID.randomUUID())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .expiryDate(LocalDate.now().plusMonths(6))
                .build();

        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(activeMembership));

        assertTrue(membershipService.hasActiveMembership(userUuid));

        Membership expiredMembership = Membership.builder()
                .uuid(UUID.randomUUID())
                .userUuid(userUuid)
                .status(MembershipStatus.ACTIVE)
                .expiryDate(LocalDate.now().minusDays(1))
                .build();

        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.of(expiredMembership));

        assertFalse(membershipService.hasActiveMembership(userUuid));

        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), any()))
                .thenReturn(Optional.empty());

        assertFalse(membershipService.hasActiveMembership(userUuid));
    }

    @Test
    void testEvictActiveMembershipCache() {
        assertDoesNotThrow(() -> membershipService.evictActiveMembershipCache(userUuid));
    }
}
