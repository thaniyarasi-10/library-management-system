package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.dto.MembershipResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MembershipMapper {
    @Mapping(source = "signed", target = "isSigned")
    MembershipResponseDto mapToResponse(Membership membership);
}
