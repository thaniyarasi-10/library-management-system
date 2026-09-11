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
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MembershipServiceImplTest {

    @Mock
    private MembershipRepository membershipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private S3Service s3Service;

    @Spy
    private MembershipMapper membershipMapper = Mappers.getMapper(MembershipMapper.class);

    @InjectMocks
    private MembershipServiceImpl membershipService;

    private User user;
    private Membership membership;
    private UUID userUuid;
    private UUID membershipUuid;

    @BeforeEach
    void setUp() {
        userUuid = UUID.randomUUID();
        membershipUuid = UUID.randomUUID();

        user = User.builder()
                .uuid(userUuid)
                .name("John Doe")
                .email("john@example.com")
                .build();

        membership = Membership.builder()
                .uuid(membershipUuid)
                .userUuid(userUuid)
                .status(MembershipStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .signed(false)
                .build();

        ReflectionTestUtils.setField(membershipService, "membershipBucketName", "test-bucket");
        ReflectionTestUtils.setField(membershipService, "membershipBucketRegion", "us-east-1");
        ReflectionTestUtils.setField(membershipService, "membershipTemplateKey", "templates/agreement.html");
    }

    @Test
    void applyForMembership_success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserUuidAndStatusIn(eq(userUuid), anyList())).thenReturn(false);
        when(membershipRepository.save(any(Membership.class))).thenReturn(membership);
        when(s3Service.downloadFileAsString(anyString(), anyString(), anyString())).thenReturn("<html>{{MEMBER_NAME}}</html>");

        MembershipApplicationResponse response = membershipService.applyForMembership("john@example.com");

        assertNotNull(response);
        assertEquals(membershipUuid, response.membershipUuid());
        assertTrue(response.agreementHtml().contains("John Doe"));
    }

    @Test
    void applyForMembership_whenUserNotFound_throwsException() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> membershipService.applyForMembership("john@example.com"));
    }

    @Test
    void applyForMembership_whenAlreadyExists_throwsException() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.existsByUserUuidAndStatusIn(eq(userUuid), anyList())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> membershipService.applyForMembership("john@example.com"));
    }

    @Test
    void signAgreement_emptyFile_throwsException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "sig.png", "image/png", new byte[0]);

        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(membershipUuid, emptyFile, "john@example.com"));
    }

    @Test
    void signAgreement_wrongContentType_throwsException() {
        MockMultipartFile file = new MockMultipartFile("file", "sig.jpg", "image/jpeg", new byte[]{1, 2, 3});

        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(membershipUuid, file, "john@example.com"));
    }

    @Test
    void signAgreement_oversizedFile_throwsException() {
        byte[] largeBytes = new byte[51 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "sig.png", "image/png", largeBytes);

        assertThrows(ResponseStatusException.class,
                () -> membershipService.signAgreement(membershipUuid, file, "john@example.com"));
    }

    @Test
    void getMyMembership_success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(membership));

        MembershipResponseDto response = membershipService.getMyMembership("john@example.com");

        assertNotNull(response);
    }

    @Test
    void cancelMembership_success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(membership));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MembershipResponseDto response = membershipService.cancelMembership("john@example.com");

        assertNotNull(response);
        verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void getAgreementHtmlByUuid_success() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.findByUuid(membershipUuid)).thenReturn(Optional.of(membership));
        when(s3Service.downloadFileAsString(anyString(), anyString(), anyString())).thenReturn("<html>{{MEMBER_NAME}}</html>");

        String html = membershipService.getAgreementHtmlByUuid(membershipUuid, "john@example.com");

        assertNotNull(html);
        assertTrue(html.contains("John Doe"));
    }

    @Test
    void downloadAgreementPdf_success() {
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setSignedPdfKey("signed-agreements/12345-signed.pdf");

        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(user));
        when(membershipRepository.findByMembershipId(12345L)).thenReturn(Optional.of(membership));
        when(s3Service.downloadFile(anyString(), anyString(), anyString())).thenReturn(new byte[]{1, 2, 3});

        byte[] pdf = membershipService.downloadAgreementPdf(12345L, "john@example.com");

        assertNotNull(pdf);
        assertEquals(3, pdf.length);
    }

    @Test
    void hasActiveMembership_trueWhenActiveAndNotExpired() {
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setExpiryDate(LocalDate.now().plusDays(30));

        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.of(membership));

        assertTrue(membershipService.hasActiveMembership(userUuid));
    }

    @Test
    void hasActiveMembership_falseWhenNoneFound() {
        when(membershipRepository.findTopByUserUuidAndStatusInOrderByCreatedAtDesc(eq(userUuid), anyList()))
                .thenReturn(Optional.empty());

        assertFalse(membershipService.hasActiveMembership(userUuid));
    }
}
