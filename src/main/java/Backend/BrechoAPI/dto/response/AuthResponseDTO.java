package Backend.BrechoAPI.dto.response;


import Backend.BrechoAPI.domain.enums.Role;

import java.util.UUID;






public record AuthResponseDTO(
        String accessToken,
        String tokenType,
        long expiresIn,
        UUID userId,
        String name,
        Role role
) {
    public static AuthResponseDTO bearer(String accessToken, long expiresIn,
                                         UUID userId, String name, Role role) {
        return new AuthResponseDTO(accessToken, "Bearer", expiresIn, userId, name, role);
    }
}
