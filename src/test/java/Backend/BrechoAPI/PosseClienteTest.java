package Backend.BrechoAPI;

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

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;





























@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PosseClienteTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired MockMvc mockMvc;

    @Test
    void clienteNaoEditaEnderecoDeOutroCliente() throws Exception {
        String tokenA = registrarELogar("posse-end-a");

        String respostaA = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEndereco()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID addressIdDeA = UUID.fromString(
                respostaA.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));

        String tokenB = registrarELogar("posse-end-b");

        mockMvc.perform(put("/me/addresses/{id}", addressIdDeA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEndereco()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));
    }

    




    @Test
    void clienteEditaOProprioEndereco() throws Exception {
        String tokenA = registrarELogar("posse-end-gemeo");

        String respostaA = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEndereco()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID addressIdDeA = UUID.fromString(
                respostaA.replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1"));

        mockMvc.perform(put("/me/addresses/{id}", addressIdDeA)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoEndereco()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addressIdDeA.toString()));
    }

    




    @Test
    void carrinhoDeUmClienteNaoVazaParaOutro() throws Exception {
        String emailA = novoEmail("posse-cart-a");
        registrar(emailA);
        String tokenA = login(emailA);

        UUID productId = criarPeca();

        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(productId)))
                .andExpect(status().isOk());

        String tokenB = registrarELogar("posse-cart-b");

        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()));
    }

    

    @Autowired Backend.BrechoAPI.repository.UserRepository userRepository;
    @Autowired Backend.BrechoAPI.repository.CategoryRepository categoryRepository;
    @Autowired Backend.BrechoAPI.repository.ProductRepository productRepository;

    


    private UUID criarPeca() {
        String m = UUID.randomUUID().toString().substring(0, 8);
        Backend.BrechoAPI.domain.User dona = userRepository.save(
                Backend.BrechoAPI.domain.User.createWithRole(
                        "Dona " + m, "dona-" + m + "@teste.local", "hash",
                        Backend.BrechoAPI.domain.enums.Role.SELLER));
        Backend.BrechoAPI.domain.Category categoria = categoryRepository.save(
                Backend.BrechoAPI.domain.Category.create("Cat " + m, "cat-" + m,
                        Backend.BrechoAPI.domain.enums.CategorySizeType.ALPHA));
        Backend.BrechoAPI.domain.Product produto = productRepository.save(
                Backend.BrechoAPI.domain.Product.create(dona, categoria, "Peça " + m, "desc",
                        "Marca", "M", null, "azul",
                        Backend.BrechoAPI.domain.enums.ProductCondition.GOOD, new BigDecimal("70.00")));

        return produto.getId();
    }

    private String registrarELogar(String prefixo) throws Exception {
        String email = novoEmail(prefixo);
        registrar(email);
        return login(email);
    }

    private void registrar(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente","email":"%s","password":"senha-forte-123",
                                 "phone":"11987654321"}""".formatted(email)))
                .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String resposta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return resposta.replaceAll(".*\"accessToken\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }

    private String novoEmail(String prefixo) {
        return prefixo + "-" + UUID.randomUUID() + "@teste.local";
    }

    private String corpoEndereco() {
        return """
                {"zipCode":"01310100","street":"Av. Paulista","number":"1000",
                 "district":"Bela Vista","city":"São Paulo","uf":"SP"}""";
    }

    private String corpoAddItem(UUID productId) {
        return """
                {"productId":"%s"}""".formatted(productId);
    }
}
