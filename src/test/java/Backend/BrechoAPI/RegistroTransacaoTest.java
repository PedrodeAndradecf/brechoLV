package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Cart;
import Backend.BrechoAPI.repository.CartRepository;
import Backend.BrechoAPI.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


















@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistroTransacaoTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;

    

    @MockitoBean CartRepository cartRepository;

    @Test
    void falhaAoGravarOCarrinhoDesfazAContaInteira() throws Exception {
        String email = "c-" + UUID.randomUUID() + "@teste.local";

        when(cartRepository.save(any(Cart.class)))
                .thenThrow(new DataIntegrityViolationException("falha simulada ao gravar o carrinho"));

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente","email":"%s","password":"senha-forte-123",
                                 "phone":"11987654321"}""".formatted(email)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("PERSISTENCE_FAILURE"));

        assertThat(userRepository.findByEmailAndDeletedAtIsNull(email))
                .as("o usuário não pode sobreviver à falha do carrinho — "
                        + "conta sem carrinho é o estado que a decisão de criar no registro elimina")
                .isEmpty();
    }
}
