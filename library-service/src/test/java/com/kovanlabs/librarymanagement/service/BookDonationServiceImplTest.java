package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
import com.kovanlabs.librarymanagement.aws.s3.service.S3Service;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.BookDonation;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.entity.UserProvider;
import com.kovanlabs.librarymanagement.database.enums.DonationStatus;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.repository.BookDonationRepository;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.dto.BookDonationRequest;
import com.kovanlabs.librarymanagement.dto.BookDonationResponse;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.kovanlabs.librarymanagement.database.repository.RewardRepository;

@ExtendWith(MockitoExtension.class)
class BookDonationServiceImplTest {

    @Mock
    private BookDonationRepository bookDonationRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProviderRepository userProviderRepository;

    @Mock
    private RewardRepository rewardRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private SalesforceSync salesforceSyncService;

    @InjectMocks
    private BookDonationServiceImpl bookDonationService;

    private User standardUser;
    private User adminUser;
    private Book existingBook;
    private BookDonation sampleDonation;

    @BeforeEach
    void setUp() {
        standardUser = User.builder()
                .uuid(UUID.randomUUID().toString())
                .id(1L)
                .email("user@example.com")
                .name("John Doe")
                .role(RoleEnum.USER)
                .build();

        adminUser = User.builder()
                .uuid(UUID.randomUUID().toString())
                .id(2L)
                .email("admin@example.com")
                .name("Admin User")
                .role(RoleEnum.ADMIN)
                .build();

        existingBook = Book.builder()
                .uuid(UUID.randomUUID().toString())
                .id(10L)
                .title("Clean Code")
                .author("Robert C. Martin")
                .isbn("9780132350884")
                .totalBookCount(5)
                .borrowedBookCount(1)
                .salesforceSyncStatus(SalesforceSyncStatus.SUCCESS)
                .salesforceRetryCount(0)
                .build();

        sampleDonation = BookDonation.builder()
                .uuid(UUID.randomUUID().toString())
                .id(100L)
                .user(standardUser)
                .title("Clean Code")
                .author("Robert C. Martin")
                .isbn("9780132350884")
                .donatedBookCount(3)
                .status(DonationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // 1. CREATE DONATION

    @Test
    @DisplayName("createDonation - Success with user found directly by providerId")
    void createDonation_success_directProviderId() {
        BookDonationRequest request = new BookDonationRequest(
                "Clean Code", "Robert C. Martin", "9780132350884", 2
        );

        UserProvider up = UserProvider.builder().providerId("google-oauth2|999").userUuid(standardUser.getUuid()).build();
        when(userProviderRepository.findByProviderId("google-oauth2|999")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(standardUser.getUuid())).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> {
            BookDonation d = inv.getArgument(0);
            d.setUuid(UUID.randomUUID().toString());
            d.setId(101L);
            return d;
        });

        BookDonationResponse response = bookDonationService.createDonation(request, null, "google-oauth2|999");

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
        assertEquals("Robert C. Martin", response.author());
        assertEquals("9780132350884", response.isbn());
        assertEquals(2, response.donatedBookCount());
        assertEquals(DonationStatus.PENDING, response.status());
        assertEquals(standardUser.getId(), response.userId());
        verify(bookDonationRepository).save(any(BookDonation.class));
    }

    @Test
    @DisplayName("createDonation - Success with user found by email")
    void createDonation_success_userFoundByEmail() {
        BookDonationRequest request = new BookDonationRequest(
                "Clean Code", "Robert C. Martin", "9780132350884", 2
        );

        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> {
            BookDonation d = inv.getArgument(0);
            d.setUuid(UUID.randomUUID().toString());
            d.setId(101L);
            return d;
        });

        BookDonationResponse response = bookDonationService.createDonation(request, null, "user@example.com");

        assertNotNull(response);
        assertEquals("Clean Code", response.title());
        assertEquals("Robert C. Martin", response.author());
        assertEquals("9780132350884", response.isbn());
        assertEquals(2, response.donatedBookCount());
        assertEquals(DonationStatus.PENDING, response.status());
        assertEquals(standardUser.getId(), response.userId());
        verify(bookDonationRepository).save(any(BookDonation.class));
    }

    @Test
    @DisplayName("createDonation - Success with user found by providerId with pipe (|)")
    void createDonation_success_userFoundByProviderWithPipe() {
        BookDonationRequest request = new BookDonationRequest(
                "Design Patterns", "Gang of Four", "9780201633610", 1
        );

        UserProvider up = UserProvider.builder().providerId("auth0|12345").userUuid(standardUser.getUuid()).build();
        when(userProviderRepository.findByProviderId("auth0|12345")).thenReturn(Optional.empty());
        when(userProviderRepository.findByProviderId("12345")).thenReturn(Optional.of(up));
        when(userRepository.findByUuid(standardUser.getUuid())).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.createDonation(request, null, "auth0|12345");

        assertNotNull(response);
        assertEquals("Design Patterns", response.title());
        verify(bookDonationRepository).save(any(BookDonation.class));
    }

    @Test
    @DisplayName("createDonation - Success when pipe prefix present but raw ID is empty, falls back to email")
    void createDonation_success_pipeWithEmptyRawId() {
        BookDonationRequest request = new BookDonationRequest("Book", "Author", "ISBN1", 1);
        when(userProviderRepository.findByProviderId("auth0|")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("auth0|")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.createDonation(request, null, "auth0|");

        assertNotNull(response);
        assertEquals("Book", response.title());
    }

    @Test
    @DisplayName("createDonation - Success with user found by UUID")
    void createDonation_success_userFoundByUuid() {
        BookDonationRequest request = new BookDonationRequest(
                "Refactoring", "Martin Fowler", "9780201485677", 4
        );

        when(userProviderRepository.findByProviderId(standardUser.getUuid())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(standardUser.getUuid())).thenReturn(Optional.empty());
        when(userRepository.findByUuid(standardUser.getUuid())).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.createDonation(request, null, standardUser.getUuid());

        assertNotNull(response);
        assertEquals("Refactoring", response.title());
    }

    @Test
    @DisplayName("createDonation - Fails when request is null")
    void createDonation_fails_nullRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(null, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Donation request cannot be null", ex.getReason());
    }

    @Test
    @DisplayName("createDonation - Fails when title is null or blank")
    void createDonation_fails_missingTitle() {
        BookDonationRequest reqNull = new BookDonationRequest(null, "Author", "ISBN123", 1);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(reqNull, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());
        assertEquals("Book title is required", ex1.getReason());

        BookDonationRequest reqBlank = new BookDonationRequest("   ", "Author", "ISBN123", 1);
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(reqBlank, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        assertEquals("Book title is required", ex2.getReason());
    }

    @Test
    @DisplayName("createDonation - Fails when author is null or blank")
    void createDonation_fails_missingAuthor() {
        BookDonationRequest reqNull = new BookDonationRequest("Title", null, "ISBN123", 1);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(reqNull, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());
        assertEquals("Book author is required", ex1.getReason());

        BookDonationRequest reqBlank = new BookDonationRequest("Title", "   ", "ISBN123", 1);
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(reqBlank, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        assertEquals("Book author is required", ex2.getReason());
    }

    @Test
    @DisplayName("createDonation - Fails when ISBN is null or blank")
    void createDonation_fails_missingIsbn() {
        BookDonationRequest reqNull = new BookDonationRequest("Title", "Author", null, 1);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(reqNull, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());
        assertEquals("Book ISBN is required", ex1.getReason());

        BookDonationRequest reqBlank = new BookDonationRequest("Title", "Author", "   ", 1);
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(reqBlank, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
        assertEquals("Book ISBN is required", ex2.getReason());
    }

    @Test
    @DisplayName("createDonation - Fails when donatedBookCount is null or <= 0")
    void createDonation_fails_invalidQuantity() {
        BookDonationRequest requestNull = new BookDonationRequest("Title", "Author", "ISBN123", null);
        ResponseStatusException ex1 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(requestNull, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex1.getStatusCode());

        BookDonationRequest requestZero = new BookDonationRequest("Title", "Author", "ISBN123", 0);
        ResponseStatusException ex2 = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(requestZero, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex2.getStatusCode());
    }

    @Test
    @DisplayName("createDonation - Fails when user is not found")
    void createDonation_fails_userNotFound() {
        BookDonationRequest request = new BookDonationRequest("Title", "Author", "ISBN123", 1);
        when(userProviderRepository.findByProviderId("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByUuid("unknown@example.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(request, null, "unknown@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("createDonation - Fails when user identifier is null or blank")
    void createDonation_fails_blankUserIdentifier() {
        BookDonationRequest request = new BookDonationRequest("Title", "Author", "ISBN123", 1);
        ResponseStatusException exNull = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(request, null, null));
        assertEquals(HttpStatus.BAD_REQUEST, exNull.getStatusCode());

        ResponseStatusException exBlank = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(request, null, "   "));
        assertEquals(HttpStatus.BAD_REQUEST, exBlank.getStatusCode());
    }

    // 2. GET MY DONATIONS

    @Test
    @DisplayName("getMyDonations - Success returning donations list")
    void getMyDonations_success() {
        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.findByUser_UuidOrderByCreatedAtDesc(standardUser.getUuid()))
                .thenReturn(List.of(sampleDonation));

        List<BookDonationResponse> result = bookDonationService.getMyDonations("user@example.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(sampleDonation.getId(), result.get(0).id());
        verify(bookDonationRepository).findByUser_UuidOrderByCreatedAtDesc(standardUser.getUuid());
    }

    @Test
    @DisplayName("getMyDonations - Returns empty list when userIdentifier is null or blank")
    void getMyDonations_nullOrBlankUserIdentifier() {
        assertTrue(bookDonationService.getMyDonations(null).isEmpty());
        assertTrue(bookDonationService.getMyDonations("   ").isEmpty());
        verifyNoInteractions(bookDonationRepository);
    }

    // 3. GET DONATION BY UUID

    @Test
    @DisplayName("getDonationByUuid - Success for owner user")
    void getDonationByUuid_success_owner() {
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));

        BookDonationResponse response = bookDonationService.getDonationByUuid(sampleDonation.getUuid(), "user@example.com");

        assertNotNull(response);
        assertEquals(sampleDonation.getId(), response.id());
    }

    @Test
    @DisplayName("getDonationByUuid - Success for admin even if not owner")
    void getDonationByUuid_success_admin() {
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));

        BookDonationResponse response = bookDonationService.getDonationByUuid(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(sampleDonation.getId(), response.id());
    }

    @Test
    @DisplayName("getDonationByUuid - Success looking up by numeric ID")
    void getDonationByUuid_success_numericId() {
        when(bookDonationRepository.findByUuid("100")).thenReturn(Optional.empty());
        when(bookDonationRepository.findById(100L)).thenReturn(Optional.of(sampleDonation));
        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));

        BookDonationResponse response = bookDonationService.getDonationByUuid("100", "admin@example.com");

        assertNotNull(response);
        assertEquals(100L, response.id());
    }

    @Test
    @DisplayName("getDonationByUuid - Fails when numeric ID not found in findById")
    void getDonationByUuid_fails_numericIdNotFound() {
        when(bookDonationRepository.findByUuid("9999")).thenReturn(Optional.empty());
        when(bookDonationRepository.findById(9999L)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.getDonationByUuid("9999", "admin@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("getDonationByUuid - Forbidden for non-admin non-owner user")
    void getDonationByUuid_forbidden_nonOwner() {
        User otherUser = User.builder()
                .uuid(UUID.randomUUID().toString())
                .id(3L)
                .email("other@example.com")
                .role(RoleEnum.USER)
                .build();

        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(userProviderRepository.findByProviderId("other@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.getDonationByUuid(sampleDonation.getUuid(), "other@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Access denied: You can only view your own donation records", ex.getReason());
    }

    @Test
    @DisplayName("getDonationByUuid - Forbidden when donation has null user and caller is not admin")
    void getDonationByUuid_forbidden_nullUserOnDonation() {
        sampleDonation.setUser(null);
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.getDonationByUuid(sampleDonation.getUuid(), "user@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("getDonationByUuid - Fails when donation not found (non-numeric string)")
    void getDonationByUuid_fails_notFound_nonNumeric() {
        when(bookDonationRepository.findByUuid("invalid-uuid-abc")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.getDonationByUuid("invalid-uuid-abc", "admin@example.com"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    @DisplayName("getDonationByUuid - Fails when identifier is blank")
    void getDonationByUuid_fails_blankIdentifier() {
        ResponseStatusException exNull = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.getDonationByUuid(null, "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, exNull.getStatusCode());

        ResponseStatusException exBlank = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.getDonationByUuid("   ", "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, exBlank.getStatusCode());
    }

    // 4. GET ALL DONATIONS

    @Test
    @DisplayName("getAllDonations - Success returning all donations")
    void getAllDonations_success() {
        when(bookDonationRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(sampleDonation));

        List<BookDonationResponse> result = bookDonationService.getAllDonations();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(sampleDonation.getId(), result.get(0).id());
        verify(bookDonationRepository).findAllByOrderByCreatedAtDesc();
    }

    // 5. APPROVE DONATION

    @Test
    @DisplayName("approveDonation - Success when book already exists, cover details updated, and Salesforce sync succeeds")
    void approveDonation_success_existingBook_updateCovers_sfSuccess() {
        existingBook.setCoverImageUrl(null);
        existingBook.setCoverImageKey(null);
        existingBook.setTotalBookCount(null);
        sampleDonation.setCoverImageUrl("http://cover.jpg");
        sampleDonation.setCoverImageKey("cover-key-123");
        sampleDonation.setDonatedBookCount(null); // tests fallback count = 1

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        assertNotNull(response.reviewedAt());
        assertEquals(existingBook.getId(), response.bookId());
        assertEquals(1, existingBook.getTotalBookCount());
        assertEquals("http://cover.jpg", existingBook.getCoverImageUrl());
        assertEquals("cover-key-123", existingBook.getCoverImageKey());
        verify(salesforceSyncService).syncBook(any());
        assertEquals(SalesforceSyncStatus.SUCCESS, existingBook.getSalesforceSyncStatus());
    }

    @Test
    @DisplayName("approveDonation - Success when book already has cover details and donation covers are null")
    void approveDonation_success_existingBook_coversAlreadySet_donationCoversNull() {
        existingBook.setCoverImageUrl("http://existing.cover");
        existingBook.setCoverImageKey("existing-key");
        sampleDonation.setCoverImageUrl(null);
        sampleDonation.setCoverImageKey(null);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        assertEquals("http://existing.cover", existingBook.getCoverImageUrl());
    }

    @Test
    @DisplayName("approveDonation - Success when book already exists and Salesforce sync fails (retry scheduled)")
    void approveDonation_success_existingBook_sfFails() {
        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("Salesforce down")).when(salesforceSyncService).syncBook(any());

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        assertEquals(SalesforceSyncStatus.PENDING, existingBook.getSalesforceSyncStatus());
        assertEquals(1, existingBook.getSalesforceRetryCount());
    }

    @Test
    @DisplayName("approveDonation - Success creating new book when ISBN does not exist (Salesforce service null)")
    void approveDonation_success_newBook_sfNull() {
        BookDonationServiceImpl serviceWithNullSf = new BookDonationServiceImpl(
                bookDonationRepository, bookRepository, userRepository, userProviderRepository, rewardRepository, s3Service, null
        );

        sampleDonation.setIsbn("9781111222333");
        sampleDonation.setCoverImageUrl("http://cover.img");
        sampleDonation.setCoverImageKey("cover-key");

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn("9781111222333")).thenReturn(Optional.empty());
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> {
            Book b = inv.getArgument(0);
            b.setUuid(UUID.randomUUID().toString());
            b.setId(55L);
            return b;
        });
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rewardRepository.incrementPoints(sampleDonation.getUser().getUuid(), 6)).thenReturn(0);

        BookDonationResponse response = serviceWithNullSf.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        assertEquals(55L, response.bookId());
        assertEquals(3, response.donatedBookCount());
        verify(rewardRepository).save(any());
    }

    @Test
    @DisplayName("approveDonation - Success updating existing reward points when incrementPoints returns > 0")
    void approveDonation_success_updateExistingReward() {
        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn(sampleDonation.getIsbn())).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rewardRepository.incrementPoints(sampleDonation.getUser().getUuid(), 6)).thenReturn(1);

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        verify(rewardRepository, never()).save(any());
    }

    @Test
    @DisplayName("approveDonation - Success when donation has null user (no rewards awarded)")
    void approveDonation_success_nullUser_noReward() {
        sampleDonation.setUser(null);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn(sampleDonation.getIsbn())).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        verifyNoInteractions(rewardRepository);
    }

    @Test
    @DisplayName("approveDonation - Success when donation user has null UUID (no rewards awarded)")
    void approveDonation_success_nullUserUuid_noReward() {
        User userWithoutUuid = User.builder().id(99L).email("nouuid@example.com").build();
        userWithoutUuid.setUuid(null);
        sampleDonation.setUser(userWithoutUuid);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn(sampleDonation.getIsbn())).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        verifyNoInteractions(rewardRepository);
    }

    @Test
    @DisplayName("approveDonation - Fails when caller is not an ADMIN")
    void approveDonation_fails_notAdmin() {
        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.approveDonation(sampleDonation.getUuid(), "user@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Only administrators are authorized to approve donations", ex.getReason());
    }

    @Test
    @DisplayName("approveDonation - Fails when donation is already APPROVED")
    void approveDonation_fails_alreadyApproved() {
        sampleDonation.setStatus(DonationStatus.APPROVED);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Donation is already approved", ex.getReason());
    }

    @Test
    @DisplayName("approveDonation - Fails when donation is already REJECTED")
    void approveDonation_fails_alreadyRejected() {
        sampleDonation.setStatus(DonationStatus.REJECTED);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot approve an already rejected donation", ex.getReason());
    }

    // 6. REJECT DONATION

    @Test
    @DisplayName("rejectDonation - Success rejecting pending donation with reason")
    void rejectDonation_success_withReason() {
        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.rejectDonation(sampleDonation.getUuid(), "Book is damaged", "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.REJECTED, response.status());
        assertEquals("Book is damaged", response.rejectionReason());
        assertNotNull(response.reviewedAt());
        verifyNoInteractions(bookRepository);
    }

    @Test
    @DisplayName("rejectDonation - Success rejecting pending donation with null reason")
    void rejectDonation_success_nullReason() {
        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.rejectDonation(sampleDonation.getUuid(), null, "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.REJECTED, response.status());
        assertNull(response.rejectionReason());
    }

    @Test
    @DisplayName("rejectDonation - Fails when caller is not an ADMIN")
    void rejectDonation_fails_notAdmin() {
        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.rejectDonation(sampleDonation.getUuid(), null, "user@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        assertEquals("Only administrators are authorized to reject donations", ex.getReason());
    }

    @Test
    @DisplayName("rejectDonation - Fails when donation is already APPROVED")
    void rejectDonation_fails_alreadyApproved() {
        sampleDonation.setStatus(DonationStatus.APPROVED);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.rejectDonation(sampleDonation.getUuid(), null, "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot reject an already approved donation", ex.getReason());
    }

    @Test
    @DisplayName("rejectDonation - Fails when donation is already REJECTED")
    void rejectDonation_fails_alreadyRejected() {
        sampleDonation.setStatus(DonationStatus.REJECTED);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.rejectDonation(sampleDonation.getUuid(), null, "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Donation is already rejected", ex.getReason());
    }

    @Test
    @DisplayName("rejectDonation - Success when reason is blank whitespace (should not set rejectionReason)")
    void rejectDonation_success_blankReason() {
        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.rejectDonation(sampleDonation.getUuid(), "   ", "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.REJECTED, response.status());
        assertNull(response.rejectionReason());
    }

    @Test
    @DisplayName("rejectDonation - Fails when status is null (non-pending)")
    void rejectDonation_fails_nullStatus() {
        sampleDonation.setStatus(null);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.rejectDonation(sampleDonation.getUuid(), null, "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Only pending donations can be rejected", ex.getReason());
    }

    @Test
    @DisplayName("approveDonation - Fails when status is null (non-pending)")
    void approveDonation_fails_nullStatus() {
        sampleDonation.setStatus(null);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Only pending donations can be approved", ex.getReason());
    }

    @Test
    @DisplayName("approveDonation - Success updating only coverImageKey when coverImageUrl is already set on existing book")
    void approveDonation_success_partialCoverUpdate_onlyKey() {
        existingBook.setCoverImageUrl("http://existing.cover");
        existingBook.setCoverImageKey(null);
        sampleDonation.setCoverImageUrl("http://new.cover");
        sampleDonation.setCoverImageKey("new-key-456");

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        assertEquals("http://existing.cover", existingBook.getCoverImageUrl());
        assertEquals("new-key-456", existingBook.getCoverImageKey());
    }

    @Test
    @DisplayName("approveDonation - Success updating only coverImageUrl when coverImageKey is already set on existing book")
    void approveDonation_success_partialCoverUpdate_onlyUrl() {
        existingBook.setCoverImageUrl(null);
        existingBook.setCoverImageKey("existing-key");
        sampleDonation.setCoverImageUrl("http://new.cover");
        sampleDonation.setCoverImageKey("new-key-456");

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid(sampleDonation.getUuid())).thenReturn(Optional.of(sampleDonation));
        when(bookRepository.findByIsbn("9780132350884")).thenReturn(Optional.of(existingBook));
        when(bookRepository.save(any(Book.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.approveDonation(sampleDonation.getUuid(), "admin@example.com");

        assertNotNull(response);
        assertEquals(DonationStatus.APPROVED, response.status());
        assertEquals("http://new.cover", existingBook.getCoverImageUrl());
        assertEquals("existing-key", existingBook.getCoverImageKey());
    }

    // 5. CREATE DONATION WITH COVER IMAGE

    @Test
    @DisplayName("createDonation - Success with cover image file upload")
    void createDonation_success_withCoverFile() throws IOException {
        BookDonationRequest request = new BookDonationRequest(
                "Clean Code", "Robert C. Martin", "9780132350884", 2
        );
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image-content".getBytes());
        S3UploadResponse s3Res = new S3UploadResponse("covers/test.jpg", "https://s3.amazonaws.com/bucket/test.jpg");
        when(s3Service.uploadFile(file)).thenReturn(s3Res);

        when(userProviderRepository.findByProviderId("donor@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.createDonation(request, file, "donor@example.com");

        assertNotNull(response);
        assertEquals("https://s3.amazonaws.com/bucket/test.jpg", response.coverImageUrl());
        assertEquals("covers/test.jpg", response.coverImageKey());
    }

    @Test
    @DisplayName("createDonation - Success when cover file is empty (ignored)")
    void createDonation_success_withEmptyCoverFile() {
        BookDonationRequest request = new BookDonationRequest(
                "Clean Code", "Robert C. Martin", "9780132350884", 2
        );
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "image/jpeg", new byte[0]);

        when(userProviderRepository.findByProviderId("donor@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.createDonation(request, emptyFile, "donor@example.com");

        assertNotNull(response);
        assertNull(response.coverImageUrl());
        assertNull(response.coverImageKey());
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("createDonation - Fails with 500 when S3Service throws IOException during file upload")
    void createDonation_fails_s3IOException() throws IOException {
        BookDonationRequest request = new BookDonationRequest(
                "Clean Code", "Robert C. Martin", "9780132350884", 2
        );
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", "image-content".getBytes());
        when(userProviderRepository.findByProviderId("donor@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("donor@example.com")).thenReturn(Optional.of(standardUser));
        when(s3Service.uploadFile(file)).thenThrow(new IOException("S3 connection error"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.createDonation(request, file, "donor@example.com"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Failed to upload cover image"));
    }

    // 6. UPDATE DONATION TESTS

    @Test
    @DisplayName("updateDonation - Fails with 400 when request is null")
    void updateDonation_fails_nullRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", null, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Donation request cannot be null", ex.getReason());
    }

    @Test
    @DisplayName("updateDonation - Fails with 400 when title is blank")
    void updateDonation_fails_blankTitle() {
        BookDonationRequest request = new BookDonationRequest("", "Author", "ISBN123", 2);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", request, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Book title is required", ex.getReason());
    }

    @Test
    @DisplayName("updateDonation - Fails with 400 when author is blank")
    void updateDonation_fails_blankAuthor() {
        BookDonationRequest request = new BookDonationRequest("Title", "", "ISBN123", 2);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", request, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Book author is required", ex.getReason());
    }

    @Test
    @DisplayName("updateDonation - Fails with 400 when isbn is blank")
    void updateDonation_fails_blankIsbn() {
        BookDonationRequest request = new BookDonationRequest("Title", "Author", "", 2);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", request, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Book ISBN is required", ex.getReason());
    }

    @Test
    @DisplayName("updateDonation - Fails with 400 when copy count is invalid")
    void updateDonation_fails_invalidCopyCount() {
        BookDonationRequest request = new BookDonationRequest("Title", "Author", "ISBN123", 0);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", request, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Donated book count must be greater than 0", ex.getReason());
    }

    @Test
    @DisplayName("updateDonation - Fails with 403 when user is neither owner nor admin")
    void updateDonation_fails_forbidden() {
        BookDonationRequest request = new BookDonationRequest("Title", "Author", "ISBN123", 1);
        User otherUser = User.builder().uuid("other-uuid").role(RoleEnum.USER).build();
        BookDonation don = BookDonation.builder()
                .uuid("don-1")
                .user(standardUser)
                .status(DonationStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId("other@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", request, null, "other@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("updateDonation - Fails with 400 when donation is not pending (APPROVED)")
    void updateDonation_fails_alreadyApproved() {
        BookDonationRequest request = new BookDonationRequest("Title", "Author", "ISBN123", 1);
        BookDonation don = BookDonation.builder()
                .uuid("don-1")
                .user(standardUser)
                .status(DonationStatus.APPROVED)
                .build();

        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", request, null, "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot edit a donation that has already been reviewed", ex.getReason());
    }

    @Test
    @DisplayName("updateDonation - Success by owner without file")
    void updateDonation_success_byOwner_noFile() {
        BookDonationRequest request = new BookDonationRequest("Updated Title", "Updated Author", "9781234567890", 3);
        BookDonation don = BookDonation.builder()
                .id(1L)
                .uuid("don-1")
                .user(standardUser)
                .title("Old Title")
                .author("Old Author")
                .isbn("Old ISBN")
                .donatedBookCount(1)
                .status(DonationStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.updateDonation("don-1", request, null, "user@example.com");

        assertNotNull(response);
        assertEquals("Updated Title", response.title());
        assertEquals("Updated Author", response.author());
        assertEquals("9781234567890", response.isbn());
        assertEquals(3, response.donatedBookCount());
    }

    @Test
    @DisplayName("updateDonation - Success by admin with cover image upload")
    void updateDonation_success_byAdmin_withCoverFile() throws IOException {
        BookDonationRequest request = new BookDonationRequest("Updated Title", "Updated Author", "9781234567890", 3);
        BookDonation don = BookDonation.builder()
                .id(1L)
                .uuid("don-1")
                .user(standardUser)
                .title("Old Title")
                .author("Old Author")
                .isbn("Old ISBN")
                .donatedBookCount(1)
                .status(DonationStatus.PENDING)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "updated.jpg", "image/jpeg", "content".getBytes());
        S3UploadResponse s3Res = new S3UploadResponse("covers/updated.jpg", "https://s3.amazonaws.com/bucket/updated.jpg");
        when(s3Service.uploadFile(file)).thenReturn(s3Res);

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));
        when(bookDonationRepository.save(any(BookDonation.class))).thenAnswer(inv -> inv.getArgument(0));

        BookDonationResponse response = bookDonationService.updateDonation("don-1", request, file, "admin@example.com");

        assertNotNull(response);
        assertEquals("https://s3.amazonaws.com/bucket/updated.jpg", response.coverImageUrl());
        assertEquals("covers/updated.jpg", response.coverImageKey());
    }

    @Test
    @DisplayName("updateDonation - Fails with 500 when S3Service throws IOException")
    void updateDonation_fails_s3IOException() throws IOException {
        BookDonationRequest request = new BookDonationRequest("Updated Title", "Updated Author", "9781234567890", 3);
        BookDonation don = BookDonation.builder()
                .id(1L)
                .uuid("don-1")
                .user(standardUser)
                .status(DonationStatus.PENDING)
                .build();

        MockMultipartFile file = new MockMultipartFile("file", "updated.jpg", "image/jpeg", "content".getBytes());
        when(s3Service.uploadFile(file)).thenThrow(new IOException("S3 failure"));

        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.updateDonation("don-1", request, file, "user@example.com"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Failed to upload cover image"));
    }

    // 7. DELETE DONATION TESTS

    @Test
    @DisplayName("deleteDonation - Fails with 403 when user is neither owner nor admin")
    void deleteDonation_fails_forbidden() {
        User otherUser = User.builder().uuid("other-uuid").role(RoleEnum.USER).build();
        BookDonation don = BookDonation.builder()
                .uuid("don-1")
                .user(standardUser)
                .status(DonationStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId("other@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(otherUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.deleteDonation("don-1", "other@example.com"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    @DisplayName("deleteDonation - Fails with 400 when donation is not pending (REJECTED)")
    void deleteDonation_fails_alreadyReviewed() {
        BookDonation don = BookDonation.builder()
                .uuid("don-1")
                .user(standardUser)
                .status(DonationStatus.REJECTED)
                .build();

        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bookDonationService.deleteDonation("don-1", "user@example.com"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals("Cannot delete a donation that has already been reviewed", ex.getReason());
    }

    @Test
    @DisplayName("deleteDonation - Success by owner")
    void deleteDonation_success_byOwner() {
        BookDonation don = BookDonation.builder()
                .uuid("don-1")
                .user(standardUser)
                .status(DonationStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(standardUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));

        assertDoesNotThrow(() -> bookDonationService.deleteDonation("don-1", "user@example.com"));
        verify(bookDonationRepository).delete(don);
    }

    @Test
    @DisplayName("deleteDonation - Success by admin")
    void deleteDonation_success_byAdmin() {
        BookDonation don = BookDonation.builder()
                .uuid("don-1")
                .user(standardUser)
                .status(DonationStatus.PENDING)
                .build();

        when(userProviderRepository.findByProviderId("admin@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(bookDonationRepository.findByUuid("don-1")).thenReturn(Optional.of(don));

        assertDoesNotThrow(() -> bookDonationService.deleteDonation("don-1", "admin@example.com"));
        verify(bookDonationRepository).delete(don);
    }
}

