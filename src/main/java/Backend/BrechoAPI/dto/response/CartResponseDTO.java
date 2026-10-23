package Backend.BrechoAPI.dto.response;

import java.util.List;
import java.util.UUID;




public record CartResponseDTO(UUID cartId, List<CartItemResponseDTO> items) {}
