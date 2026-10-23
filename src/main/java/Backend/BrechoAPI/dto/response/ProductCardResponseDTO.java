package Backend.BrechoAPI.dto.response;


import java.math.BigDecimal;
import java.util.UUID;

public record ProductCardResponseDTO(
        UUID id,
        String title,
        String brand,
        String size,
        String color,
        BigDecimal price,
        String coverPhotoUrl
) {}