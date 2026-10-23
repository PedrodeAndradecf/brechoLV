package Backend.BrechoAPI.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import Backend.BrechoAPI.validation.MaxBytes;






public record RegisterRequestDTO(

        @NotBlank(message = "nome é obrigatório")
        @Size(max = 120)
        String name,

        @NotBlank(message = "e-mail é obrigatório")
        @Email(message = "e-mail inválido")
        @Size(max = 180)
        String email,

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        @NotBlank(message = "senha é obrigatória")
        @Size(min = 8, message = "senha deve ter ao menos 8 caracteres")
        @MaxBytes(value = 72, message = "senha longa demais (máximo 72 bytes)")
        String password,

        
        
        
        @NotBlank(message = "telefone é obrigatório")
        @Pattern(regexp = "\\D*(\\d\\D*){10,11}", message = "telefone deve ter 10 ou 11 dígitos (DDD + número)")
        String phone
) {}
