package com.kovanlabs.librarymanagement.mapping;

import com.kovanlabs.librarymanagement.database.entity.Fine;
import com.kovanlabs.librarymanagement.dto.FineResponseDto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FineMapper {

    FineResponseDto mapToResponse(Fine fine);

    List<FineResponseDto> mapToResponse(List<Fine> fines);
}
