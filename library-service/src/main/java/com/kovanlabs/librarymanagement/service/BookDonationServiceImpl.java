package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.aws.s3.dto.S3UploadResponse;
import com.kovanlabs.librarymanagement.aws.s3.service.S3Service;
import com.kovanlabs.librarymanagement.database.entity.Book;
import com.kovanlabs.librarymanagement.database.entity.BookDonation;
import com.kovanlabs.librarymanagement.database.entity.User;
import com.kovanlabs.librarymanagement.database.enums.DonationStatus;
import com.kovanlabs.librarymanagement.database.enums.RoleEnum;
import com.kovanlabs.librarymanagement.database.enums.SalesforceSyncStatus;
import com.kovanlabs.librarymanagement.database.entity.Reward;
import com.kovanlabs.librarymanagement.database.repository.BookDonationRepository;
import com.kovanlabs.librarymanagement.database.repository.BookRepository;
import com.kovanlabs.librarymanagement.database.repository.RewardRepository;
import com.kovanlabs.librarymanagement.database.repository.UserProviderRepository;
import com.kovanlabs.librarymanagement.database.repository.UserRepository;
import com.kovanlabs.librarymanagement.dto.BookDonationRequest;
import com.kovanlabs.librarymanagement.dto.BookDonationResponse;
import com.kovanlabs.librarymanagement.mapping.BookDonationMapper;
import com.kovanlabs.librarymanagement.mapping.BookMapper;
import com.kovanlabs.librarymanagement.salesforce.service.SalesforceSync;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookDonationServiceImpl implements BookDonationService {

    private final BookDonationRepository bookDonationRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final UserProviderRepository userProviderRepository;
    private final RewardRepository rewardRepository;
    private final S3Service s3Service;
    private final SalesforceSync salesforceSyncService;

    /**
     * Creates a new book donation request in {@link DonationStatus#PENDING} status.
     * <p>
     * Validates required book details (title, author, ISBN, copy count) and authenticates the submitting user.
     * If an image file is attached, it uploads the file to Amazon S3 via {@link S3Service} and persists
     * the resulting S3 key and URL on the donation entity.
     * </p>
     *
     * @param request  the book donation payload containing title, author, ISBN, and copy count
     * @param file     the optional multipart image file for the book cover
     * @param userUuid the unique identifier (Auth0 sub, email, or UUID) of the donor user
     * @return the saved {@link BookDonationResponse}
     * @throws ResponseStatusException if request validation fails or donor user is not found
     */
    @Override
    @Transactional
    public BookDonationResponse createDonation(BookDonationRequest request, MultipartFile file, String userUuid) {
        if (Objects.isNull(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation request cannot be null");
        }
        if (Objects.isNull(request.title()) || request.title().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book title is required");
        }
        if (Objects.isNull(request.author()) || request.author().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book author is required");
        }
        if (Objects.isNull(request.isbn()) || request.isbn().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book ISBN is required");
        }
        if (Objects.isNull(request.donatedBookCount()) || request.donatedBookCount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donated book count must be greater than 0");
        }

        User user = findUserByIdentifier(userUuid);

        String coverImageUrl = null;
        String coverImageKey = null;

        if (Objects.nonNull(file) && !file.isEmpty()) {
            try {
                S3UploadResponse uploadResponse = s3Service.uploadFile(file);
                coverImageKey = uploadResponse.coverImageKey();
                coverImageUrl = uploadResponse.coverImageUrl();
                log.info("Uploaded cover image for new donation request: {}", coverImageKey);
            } catch (IOException e) {
                log.error("Failed to upload cover image for book donation: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload cover image: " + e.getMessage());
            }
        }

        BookDonation donation = BookDonation.builder()
                .user(user)
                .title(request.title().trim())
                .author(request.author().trim())
                .isbn(request.isbn().trim())
                .coverImageUrl(coverImageUrl)
                .coverImageKey(coverImageKey)
                .donatedBookCount(request.donatedBookCount())
                .status(DonationStatus.PENDING)
                .build();

        BookDonation savedDonation = bookDonationRepository.save(donation);
        log.info("Book donation created with UUID: {} for user: {}", savedDonation.getUuid(), user.getEmail());
        return BookDonationMapper.INSTANCE.mapToResponse(savedDonation);
    }

    /**
     * Retrieves all book donation records submitted by the authenticated user.
     *
     * @param userUuid the unique identifier (Auth0 sub, email, or UUID) of the donor user
     * @return a list of {@link BookDonationResponse} belonging to the user, ordered by creation date descending
     */
    @Override
    public List<BookDonationResponse> getMyDonations(String userUuid) {
        if (Objects.isNull(userUuid) || userUuid.isBlank()) {
            return Collections.emptyList();
        }
        User user = findUserByIdentifier(userUuid);
        List<BookDonation> donations = bookDonationRepository.findByUser_UuidOrderByCreatedAtDesc(user.getUuid());
        return BookDonationMapper.INSTANCE.mapToResponse(donations);
    }

    /**
     * Retrieves a single book donation by its ID or UUID.
     * <p>
     * Enforces ownership validation: only the donor who submitted the donation or an administrator
     * is authorized to view the record.
     * </p>
     *
     * @param donationUuid the numeric ID or UUID string of the donation record
     * @param userUuid     the unique identifier of the requesting user
     * @return the {@link BookDonationResponse} details
     * @throws ResponseStatusException if the donation is not found or the user is not authorized
     */
    @Override
    public BookDonationResponse getDonationByUuid(String donationUuid, String userUuid) {
        BookDonation donation = findDonationByIdentifier(donationUuid);
        User user = findUserByIdentifier(userUuid);

        boolean isAdmin = RoleEnum.ADMIN.equals(user.getRole());
        boolean isOwner = Objects.nonNull(donation.getUser()) && Objects.equals(donation.getUser().getUuid(), user.getUuid());

        if (!isAdmin && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You can only view your own donation records");
        }

        return BookDonationMapper.INSTANCE.mapToResponse(donation);
    }

    /**
     * Retrieves all book donation records across the entire system.
     *
     * @return a list of all {@link BookDonationResponse} records ordered by creation date descending
     */
    @Override
    public List<BookDonationResponse> getAllDonations() {
        List<BookDonation> donations = bookDonationRepository.findAllByOrderByCreatedAtDesc();
        return BookDonationMapper.INSTANCE.mapToResponse(donations);
    }

    /**
     * Updates an existing pending book donation request.
     * <p>
     * Only the owner who created the donation or an administrator may update it, and only while
     * the donation is still in {@link DonationStatus#PENDING} status.
     * </p>
     *
     * @param donationUuid the numeric ID or UUID string of the donation to update
     * @param request      the updated book donation details
     * @param file         the optional updated multipart cover image file
     * @param userUuid     the unique identifier of the user making the update request
     * @return the updated {@link BookDonationResponse}
     * @throws ResponseStatusException if request validation fails, donation not found, non-pending, or unauthorized
     */
    @Override
    @Transactional
    public BookDonationResponse updateDonation(String donationUuid, BookDonationRequest request, MultipartFile file, String userUuid) {
        if (Objects.isNull(request)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation request cannot be null");
        }
        if (Objects.isNull(request.title()) || request.title().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book title is required");
        }
        if (Objects.isNull(request.author()) || request.author().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book author is required");
        }
        if (Objects.isNull(request.isbn()) || request.isbn().trim().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Book ISBN is required");
        }
        if (Objects.isNull(request.donatedBookCount()) || request.donatedBookCount() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donated book count must be greater than 0");
        }

        User user = findUserByIdentifier(userUuid);
        BookDonation donation = findDonationByIdentifier(donationUuid);

        boolean isAdmin = RoleEnum.ADMIN.equals(user.getRole());
        boolean isOwner = Objects.nonNull(donation.getUser()) && Objects.equals(donation.getUser().getUuid(), user.getUuid());

        if (!isAdmin && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You can only edit your own pending donations");
        }

        if (!DonationStatus.PENDING.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot edit a donation that has already been reviewed");
        }

        if (Objects.nonNull(file) && !file.isEmpty()) {
            try {
                S3UploadResponse uploadResponse = s3Service.uploadFile(file);
                donation.setCoverImageKey(uploadResponse.coverImageKey());
                donation.setCoverImageUrl(uploadResponse.coverImageUrl());
                log.info("Updated cover image for donation request: {}", uploadResponse.coverImageKey());
            } catch (IOException e) {
                log.error("Failed to upload updated cover image for donation: {}", e.getMessage());
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to upload cover image: " + e.getMessage());
            }
        }

        donation.setTitle(request.title().trim());
        donation.setAuthor(request.author().trim());
        donation.setIsbn(request.isbn().trim());
        donation.setDonatedBookCount(request.donatedBookCount());

        BookDonation savedDonation = bookDonationRepository.save(donation);
        log.info("Donation [UUID: {}] updated by user [UUID: {}]", savedDonation.getUuid(), user.getUuid());
        return BookDonationMapper.INSTANCE.mapToResponse(savedDonation);
    }

    /**
     * Deletes a pending book donation request.
     * <p>
     * Only the owner who created the donation or an administrator may delete it, and only while
     * the donation is still in {@link DonationStatus#PENDING} status.
     * </p>
     *
     * @param donationUuid the numeric ID or UUID string of the donation to delete
     * @param userUuid     the unique identifier of the user making the delete request
     * @throws ResponseStatusException if donation not found, non-pending, or unauthorized
     */
    @Override
    @Transactional
    public void deleteDonation(String donationUuid, String userUuid) {
        User user = findUserByIdentifier(userUuid);
        BookDonation donation = findDonationByIdentifier(donationUuid);

        boolean isAdmin = RoleEnum.ADMIN.equals(user.getRole());
        boolean isOwner = Objects.nonNull(donation.getUser()) && Objects.equals(donation.getUser().getUuid(), user.getUuid());

        if (!isAdmin && !isOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: You can only delete your own pending donations");
        }

        if (!DonationStatus.PENDING.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete a donation that has already been reviewed");
        }

        bookDonationRepository.delete(donation);
        log.info("Donation [UUID: {}] deleted by user [UUID: {}]", donation.getUuid(), user.getUuid());
    }

    /**
     * Approves a pending book donation request (Administrator only).
     * <p>
     * Performs atomic inventory update and CRM replication:
     * <ul>
     *     <li>Checks if a {@link Book} with matching ISBN already exists in the catalog.</li>
     *     <li>If found: Increments {@code totalBookCount} and updates cover image if not previously set.</li>
     *     <li>If not found: Creates a new {@link Book} record with the donated details and quantity.</li>
     *     <li>Replicates the new/updated book state to Salesforce.</li>
     *     <li>Associates the donation with the catalog book, records admin UUID, sets status to {@link DonationStatus#APPROVED}.</li>
     * </ul>
     * </p>
     *
     * @param donationUuid the numeric ID or UUID string of the donation record to approve
     * @param adminUuid    the unique identifier of the reviewing administrator
     * @return the approved {@link BookDonationResponse}
     * @throws ResponseStatusException if caller is not admin, donation not found, or donation is not pending
     */
    @Override
    @Transactional
    public synchronized BookDonationResponse approveDonation(String donationUuid, String adminUuid) {
        User admin = findUserByIdentifier(adminUuid);
        if (!RoleEnum.ADMIN.equals(admin.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators are authorized to approve donations");
        }

        BookDonation donation = findDonationByIdentifier(donationUuid);

        if (DonationStatus.APPROVED.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation is already approved");
        }
        if (DonationStatus.REJECTED.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot approve an already rejected donation");
        }
        if (!DonationStatus.PENDING.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending donations can be approved");
        }

        int count = Objects.nonNull(donation.getDonatedBookCount()) ? donation.getDonatedBookCount() : 1;

        Optional<Book> existingBookOpt = bookRepository.findByIsbn(donation.getIsbn());
        Book targetBook;

        if (existingBookOpt.isPresent()) {
            targetBook = existingBookOpt.get();
            int currentTotal = Objects.nonNull(targetBook.getTotalBookCount()) ? targetBook.getTotalBookCount() : 0;
            targetBook.setTotalBookCount(currentTotal + count);
            if (Objects.isNull(targetBook.getCoverImageUrl()) && Objects.nonNull(donation.getCoverImageUrl())) {
                targetBook.setCoverImageUrl(donation.getCoverImageUrl());
            }
            if (Objects.isNull(targetBook.getCoverImageKey()) && Objects.nonNull(donation.getCoverImageKey())) {
                targetBook.setCoverImageKey(donation.getCoverImageKey());
            }
            targetBook = bookRepository.save(targetBook);
            log.info("Incremented total book count for existing book '{}' [ISBN: {}] by {} to {}",
                    targetBook.getTitle(), targetBook.getIsbn(), count, targetBook.getTotalBookCount());
        } else {
            targetBook = Book.builder()
                    .title(donation.getTitle())
                    .author(donation.getAuthor())
                    .isbn(donation.getIsbn())
                    .coverImageUrl(donation.getCoverImageUrl())
                    .coverImageKey(donation.getCoverImageKey())
                    .totalBookCount(count)
                    .borrowedBookCount(0)
                    .salesforceSyncStatus(SalesforceSyncStatus.PENDING)
                    .salesforceRetryCount(0)
                    .build();
            targetBook = bookRepository.save(targetBook);
            log.info("Created new book entity for approved donation '{}' [ISBN: {}] with initial total count {}",
                    targetBook.getTitle(), targetBook.getIsbn(), count);
        }

        syncBookToSalesforce(targetBook);

        // Award reward points (2 points per approved donated copy) to the donor
        if (Objects.nonNull(donation.getUser()) && Objects.nonNull(donation.getUser().getUuid()) && Objects.nonNull(rewardRepository)) {
            String donorUuid = donation.getUser().getUuid();
            int pointsEarned = count * 2;
            int updated = rewardRepository.incrementPoints(donorUuid, pointsEarned);
            if (updated == 0) {
                Reward newReward = Reward.builder()
                        .userUuid(donorUuid)
                        .points(pointsEarned)
                        .build();
                rewardRepository.save(newReward);
            }
            log.info("Awarded {} reward points to donor [UUID: {}] for approved book donation", pointsEarned, donorUuid);
        }

        donation.setBook(targetBook);
        donation.setStatus(DonationStatus.APPROVED);
        donation.setReviewedByUuid(admin.getUuid());
        donation.setReviewedAt(LocalDateTime.now());

        BookDonation savedDonation = bookDonationRepository.save(donation);
        log.info("Donation [UUID: {}] successfully approved by admin [UUID: {}]", savedDonation.getUuid(), admin.getUuid());
        return BookDonationMapper.INSTANCE.mapToResponse(savedDonation);
    }

    /**
     * Rejects a pending book donation request (Administrator only).
     * <p>
     * Marks the donation record with {@link DonationStatus#REJECTED}, attaches the admin reviewer's UUID,
     * timestamp, and optional rejection reason.
     * </p>
     *
     * @param donationUuid the numeric ID or UUID string of the donation record to reject
     * @param reason       the optional feedback explanation for rejection
     * @param adminUuid    the unique identifier of the reviewing administrator
     * @return the rejected {@link BookDonationResponse}
     * @throws ResponseStatusException if caller is not admin, donation not found, or donation is not pending
     */
    @Override
    @Transactional
    public synchronized BookDonationResponse rejectDonation(String donationUuid, String reason, String adminUuid) {
        User admin = findUserByIdentifier(adminUuid);
        if (!RoleEnum.ADMIN.equals(admin.getRole())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrators are authorized to reject donations");
        }

        BookDonation donation = findDonationByIdentifier(donationUuid);

        if (DonationStatus.APPROVED.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot reject an already approved donation");
        }
        if (DonationStatus.REJECTED.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation is already rejected");
        }
        if (!DonationStatus.PENDING.equals(donation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only pending donations can be rejected");
        }

        donation.setStatus(DonationStatus.REJECTED);
        donation.setReviewedByUuid(admin.getUuid());
        donation.setReviewedAt(LocalDateTime.now());
        if (Objects.nonNull(reason) && !reason.trim().isBlank()) {
            donation.setRejectionReason(reason.trim());
        }

        BookDonation savedDonation = bookDonationRepository.save(donation);
        log.info("Donation [UUID: {}] rejected by admin [UUID: {}]", savedDonation.getUuid(), admin.getUuid());
        return BookDonationMapper.INSTANCE.mapToResponse(savedDonation);
    }

    /**
     * Resolves a {@link User} entity flexibly from an identifier string.
     * Supports matching by Auth0 provider ID (with or without pipe prefix), email, or user UUID.
     *
     * @param identifier the input user identifier
     * @return the resolved {@link User}
     * @throws ResponseStatusException if identifier is blank or no matching user is found
     */
    private User findUserByIdentifier(String identifier) {
        if (Objects.isNull(identifier) || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User identifier is required");
        }
        return userProviderRepository.findByProviderId(identifier)
                .flatMap(up -> userRepository.findByUuid(up.getUserUuid()))
                .or(() -> {
                    if (identifier.contains("|")) {
                        String rawId = identifier.substring(identifier.indexOf('|') + 1);
                        if (!rawId.isBlank()) {
                            return userProviderRepository.findByProviderId(rawId)
                                    .flatMap(up -> userRepository.findByUuid(up.getUserUuid()));
                        }
                    }
                    return Optional.empty();
                })
                .or(() -> userRepository.findByEmail(identifier))
                .or(() -> userRepository.findByUuid(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found for identifier: " + identifier));
    }

    /**
     * Resolves a {@link BookDonation} entity by either UUID string or numeric database ID.
     *
     * @param identifier the UUID string or numeric ID
     * @return the resolved {@link BookDonation}
     * @throws ResponseStatusException if identifier is blank or donation is not found
     */
    private BookDonation findDonationByIdentifier(String identifier) {
        if (Objects.isNull(identifier) || identifier.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Donation identifier is required");
        }
        return bookDonationRepository.findByUuid(identifier)
                .or(() -> {
                    try {
                        Long numericId = Long.parseLong(identifier);
                        return bookDonationRepository.findById(numericId);
                    } catch (NumberFormatException e) {
                        return Optional.empty();
                    }
                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Donation record not found for identifier: " + identifier));
    }

    /**
     * Replicates book catalog changes to Salesforce CRM with error logging and retry incrementation.
     *
     * @param book the {@link Book} entity to synchronize
     */
    private void syncBookToSalesforce(Book book) {
        if (Objects.nonNull(salesforceSyncService)) {
            try {
                salesforceSyncService.syncBook(BookMapper.INSTANCE.toBookSObject(book));
                book.setSalesforceSyncStatus(SalesforceSyncStatus.SUCCESS);
                bookRepository.save(book);
            } catch (Exception e) {
                int retryCount = book.getSalesforceRetryCount() + 1;
                book.setSalesforceRetryCount(retryCount);
                book.setSalesforceSyncStatus(SalesforceSyncStatus.PENDING);
                bookRepository.save(book);
                log.error("Salesforce dual-write failed for book count update from donation [Book ID: {}, UUID: {}, Operation: UPSERT, RetryCount: {}]: {}",
                        book.getId(), book.getUuid(), retryCount, e.getMessage());
            }
        }
    }
}
