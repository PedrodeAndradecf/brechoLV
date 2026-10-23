package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.repository.CartRepository;
import Backend.BrechoAPI.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

























@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RegistroTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CartRepository cartRepository;

    @Test
    void registroCriaContaEOCarrinhoJunto() throws Exception {
        String email = novoEmail();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(email, "(11) 98765-4321")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.email").value(email))
                
                .andExpect(jsonPath("$.phone").value("11987654321"));

        UUID userId = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow().getId();

        assertThat(cartRepository.findByUserId(userId))
                .as("o carrinho nasce junto com a conta, não na primeira adição")
                .isPresent();
    }

    




    @Test
    void registroSempreCriaClienteMesmoComPapelNoCorpo() throws Exception {
        String email = novoEmail();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Tentativa","email":"%s","password":"senha-forte-123",
                                 "phone":"11987654321","role":"SELLER"}""".formatted(email)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow().getRole())
                .isEqualTo(Role.CLIENT);
    }

    
    @Test
    void contaRegistradaFazLoginEAcessaRotaAutenticada() throws Exception {
        String email = novoEmail();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(email, "11987654321")))
                .andExpect(status().isCreated());

        String resposta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CLIENT"))
                .andReturn().getResponse().getContentAsString();

        String token = resposta.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");

        
        mockMvc.perform(get("/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void emailJaRegistradoResponde409() throws Exception {
        String email = novoEmail();
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(email, "11987654321"))).andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(email, "11999998888")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    
    @Test
    void emailComOutraCaixaTambemEhDuplicata() throws Exception {
        String email = novoEmail();
        mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(corpo(email, "11987654321"))).andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(email.toUpperCase(), "11987654321")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void telefoneAusenteOuCurtoResponde400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Sem fone","email":"%s","password":"senha-forte-123","phone":""}"""
                                .formatted(novoEmail())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(novoEmail(), "1234")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    





    @Test
    void senhaCurtaOuLongaDemaisResponde400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"%s","password":"curta1","phone":"11987654321"}"""
                                .formatted(novoEmail())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"%s","password":"%s","phone":"11987654321"}"""
                                .formatted(novoEmail(), "a".repeat(73))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"X","email":"%s","password":"%s","phone":"11987654321"}"""
                                .formatted(novoEmail(), "á".repeat(40))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    
    @Test
    void registroNaoExigeAutenticacao() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(novoEmail(), "11987654321")))
                .andExpect(status().isCreated());
    }

    private String novoEmail() {
        return "c-" + UUID.randomUUID() + "@teste.local";
    }

    private String corpo(String email, String phone) {
        return """
                {"name":"Cliente","email":"%s","password":"senha-forte-123","phone":"%s"}"""
                .formatted(email, phone);
    }
}
