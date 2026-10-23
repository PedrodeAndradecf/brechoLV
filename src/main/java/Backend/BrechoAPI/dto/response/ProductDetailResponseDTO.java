package Backend.BrechoAPI.dto.response;

import Backend.BrechoAPI.domain.enums.ProductCondition;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record ProductDetailResponseDTO(
        UUID id,
        String title,
        String description,
        String brand,
        String size,
        String sizeLabel,
        String color,
        ProductCondition condition,
        BigDecimal price,
        CategoryResponseDTO category,
        List<ProductPhotoResponseDTO> photos
) {}
