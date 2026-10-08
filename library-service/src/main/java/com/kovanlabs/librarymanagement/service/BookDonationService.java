package com.kovanlabs.librarymanagement.service;

import com.kovanlabs.librarymanagement.dto.BookDonationRequest;
import com.kovanlabs.librarymanagement.dto.BookDonationResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BookDonationService {

    BookDonationResponse createDonation(BookDonationRequest request, MultipartFile file, String userUuid);

    List<BookDonationResponse> getMyDonations(String userUuid);

    BookDonationResponse getDonationByUuid(String donationUuid, String userUuid);

    List<BookDonationResponse> getAllDonations();

    BookDonationResponse updateDonation(String donationUuid, BookDonationRequest request, MultipartFile file, String userUuid);

    void deleteDonation(String donationUuid, String userUuid);

    BookDonationResponse approveDonation(String donationUuid, String adminUuid);

    BookDonationResponse rejectDonation(String donationUuid, String reason, String adminUuid);
}

