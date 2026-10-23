package Backend.BrechoAPI.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;





















































public record OrderResponseDTO(UUID id, String status, BigDecimal subtotal,
                               BigDecimal shippingCost, BigDecimal total,
                               Instant expiresAt, List<OrderItemResponseDTO> items,
                               ShippingResponseDTO shipping, PixResponseDTO pix) {}
