package Backend.BrechoAPI.dto.request;


import Backend.BrechoAPI.domain.enums.ProductCondition;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;


public record ProductCreateRequestDTO(

        @NotBlank(message = "título é obrigatório")
        @Size(max = 150)
        String title,

        @NotBlank(message = "descrição é obrigatória")
        @Size(max = 2000)
        String description,

        @NotBlank(message = "marca é obrigatória")
        @Size(max = 80)
        String brand,

        
        @NotBlank(message = "tamanho é obrigatório")
        @Size(max = 10)
        String size,

        
        @Size(max = 30)
        String sizeLabel,

        @NotBlank(message = "cor é obrigatória")
        @Size(max = 40)
        String color,

        @NotNull(message = "condição é obrigatória")
        ProductCondition condition,

        @NotNull(message = "preço é obrigatório")
        @DecimalMin(value = "0.01", message = "preço deve ser maior que zero")
        @Digits(integer = 8, fraction = 2, message = "preço inválido")
        BigDecimal price,

        @NotNull(message = "categoria é obrigatória")
        UUID categoryId
) {}
