package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.BookDonation;
import com.kovanlabs.librarymanagement.database.enums.DonationStatus;
import com.kovanlabs.librarymanagement.dto.BookDonationRequest;
import com.kovanlabs.librarymanagement.dto.BookDonationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(imports = {DonationStatus.class})
public interface BookDonationMapper {

    BookDonationMapper INSTANCE = Mappers.getMapper(BookDonationMapper.class);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", source = "user.name")
    @Mapping(target = "userEmail", source = "user.email")
    @Mapping(target = "bookId", source = "book.id")
    BookDonationResponse mapToResponse(BookDonation donation);

    List<BookDonationResponse> mapToResponse(List<BookDonation> donations);

    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "book", ignore = true)
    @Mapping(target = "status", expression = "java(DonationStatus.PENDING)")
    @Mapping(target = "reviewedByUuid", ignore = true)
    @Mapping(target = "reviewedAt", ignore = true)
    @Mapping(target = "rejectionReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    BookDonation mapToEntity(BookDonationRequest request);
}
