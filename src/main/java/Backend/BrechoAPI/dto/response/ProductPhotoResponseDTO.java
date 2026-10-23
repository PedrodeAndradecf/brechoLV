package Backend.BrechoAPI.dto.response;


import java.util.UUID;


public record ProductPhotoResponseDTO(
        UUID id,
        String url,
        Integer position,
        boolean cover
) {}
