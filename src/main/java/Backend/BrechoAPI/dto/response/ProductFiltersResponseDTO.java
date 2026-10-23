package Backend.BrechoAPI.dto.response;



import java.math.BigDecimal;
import java.util.List;

public record ProductFiltersResponseDTO(
        List<String> brands,
        List<String> colors,
        List<String> sizes,
        List<CategoryResponseDTO> categories,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {}