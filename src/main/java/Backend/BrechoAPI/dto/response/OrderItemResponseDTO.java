package Backend.BrechoAPI.dto.response;

import java.math.BigDecimal;
import java.util.UUID;












public record OrderItemResponseDTO(UUID productId, String title, BigDecimal unitPrice) {}
