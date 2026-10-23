package Backend.BrechoAPI.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;





public record AddressRequestDTO(
        @NotBlank @Pattern(regexp = "\\D*(\\d\\D*){8}", message = "CEP deve ter 8 dígitos")
        String zipCode,
        @NotBlank @Size(max = 150) String street,
        @NotBlank @Size(max = 20) String number,
        @Size(max = 80) String complement, 
        @NotBlank @Size(max = 80) String district,
        @NotBlank @Size(max = 80) String city,
        
        
        
        
        @NotBlank String uf
) {}
