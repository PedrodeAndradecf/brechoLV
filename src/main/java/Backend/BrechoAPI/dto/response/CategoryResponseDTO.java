package Backend.BrechoAPI.dto.response;


import Backend.BrechoAPI.domain.enums.CategorySizeType;

import java.util.UUID;



public record CategoryResponseDTO(
        UUID id,
        String name,
        String slug,
        CategorySizeType sizeType
) {}
