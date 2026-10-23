package Backend.BrechoAPI.dto.response;

import java.math.BigDecimal;
import java.util.UUID;




public record CartItemResponseDTO(
        UUID productId, String title, BigDecimal price, 
        boolean archived 
) {}
