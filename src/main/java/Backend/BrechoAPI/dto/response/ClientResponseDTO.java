package Backend.BrechoAPI.dto.response;

import java.util.UUID;







public record ClientResponseDTO(
        UUID id,
        String name,
        String email,
        String phone
) {}
