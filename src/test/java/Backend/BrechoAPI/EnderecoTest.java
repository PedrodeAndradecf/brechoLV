package Backend.BrechoAPI;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



















@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EnderecoTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

    
    @Test
    void clienteCadastraEnderecoEListaOsProprios() throws Exception {
        String token = registrarELogar();

        mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("01310100", "SP")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.city").value("São Paulo"))
                .andExpect(jsonPath("$.uf").value("SP"));

        mockMvc.perform(get("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].city").value("São Paulo"))
                .andExpect(jsonPath("$[0].uf").value("SP"));
    }

    
    @Test
    void ufInvalidaEhRecusada() throws Exception {
        String token = registrarELogar();

        mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("01310100", "XX")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"));
    }

    




    @Test
    void ufMinusculaEhAceitaENormalizada() throws Exception {
        String token = registrarELogar();

        mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("01310100", "sp")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uf").value("SP"));
    }

    





    @Test
    void ufGravadaSemPadding() throws Exception {
        String token = registrarELogar();

        String resposta = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("01310100", "sp")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID addressId = UUID.fromString(
                resposta.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));

        String ufGravada = jdbc.queryForObject(
                "SELECT uf FROM addresses WHERE id = ?", String.class, addressId);

        assertThat(ufGravada).isEqualTo("SP");
    }

    











    @Test
    void clienteEditaEnderecoOsCamposMudam() throws Exception {
        String token = registrarELogar();

        String respostaCriacao = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo("01310100", "SP")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID addressId = UUID.fromString(
                respostaCriacao.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));

        String corpoAtualizado = """
                {"zipCode":"20040020","street":"Rua da Alfândega","number":"200",
                 "district":"Centro","city":"Rio de Janeiro","uf":"RJ"}""";

        mockMvc.perform(put("/me/addresses/{id}", addressId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAtualizado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addressId.toString()))
                .andExpect(jsonPath("$.zipCode").value("20040020"))
                .andExpect(jsonPath("$.street").value("Rua da Alfândega"))
                .andExpect(jsonPath("$.city").value("Rio de Janeiro"))
                .andExpect(jsonPath("$.uf").value("RJ"));

        
        
        mockMvc.perform(get("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(addressId.toString()))
                .andExpect(jsonPath("$[0].city").value("Rio de Janeiro"))
                .andExpect(jsonPath("$[0].uf").value("RJ"));
    }

    

    private String registrarELogar() throws Exception {
        String email = novoEmail();

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente","email":"%s","password":"senha-forte-123",
                                 "phone":"11987654321"}""".formatted(email)))
                .andExpect(status().isCreated());

        String resposta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return resposta.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String novoEmail() {
        return "end-" + UUID.randomUUID() + "@teste.local";
    }

    private String corpo(String zipCode, String uf) {
        return """
                {"zipCode":"%s","street":"Av. Paulista","number":"1000",
                 "district":"Bela Vista","city":"São Paulo","uf":"%s"}"""
                .formatted(zipCode, uf);
    }
}
