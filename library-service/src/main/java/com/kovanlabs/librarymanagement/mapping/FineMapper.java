package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.dto.FineResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * MapStruct mapper for converting {@link Fine} database entities to {@link FineResponseDto}.
 */
@Mapper
public interface FineMapper {

    FineMapper INSTANCE = Mappers.getMapper(FineMapper.class);

    /**
     * Maps a single {@link Fine} entity to a {@link FineResponseDto}.
     *
     * @param fine The fine entity
     * @return The mapped {@link FineResponseDto}
     */
    FineResponseDto mapToResponse(Fine fine);

    /**
     * Maps a list of {@link Fine} entities to a list of {@link FineResponseDto}s.
     *
     * @param fines The list of fine entities
     * @return List of mapped {@link FineResponseDto}s
     */
    List<FineResponseDto> mapToResponse(List<Fine> fines);
}
