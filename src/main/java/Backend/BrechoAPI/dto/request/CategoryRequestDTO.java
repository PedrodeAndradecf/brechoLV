package Backend.BrechoAPI.dto.request;


import Backend.BrechoAPI.domain.enums.CategorySizeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequestDTO(

        @NotBlank(message = "nome é obrigatório")
        @Size(max = 80)
        String name,

        @NotBlank(message = "slug é obrigatório")
        @Size(max = 100)
        @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
                message = "slug deve conter apenas letras minúsculas, números e hífens")
        String slug,

        
        
        @NotNull(message = "sizeType é obrigatório")
        CategorySizeType sizeType
) {}
