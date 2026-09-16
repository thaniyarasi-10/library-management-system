package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Membership;
import com.kovanlabs.librarymanagement.dto.MembershipResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper for converting {@link Membership} entities to {@link MembershipResponseDto}.
 */
@Mapper(componentModel = "spring")
public interface MembershipMapper {

    /**
     * Maps a {@link Membership} entity to a {@link MembershipResponseDto}.
     *
     * @param membership The membership entity
     * @return The mapped {@link MembershipResponseDto}
     */
    @Mapping(source = "signed", target = "isSigned")
    MembershipResponseDto mapToResponse(Membership membership);
}
