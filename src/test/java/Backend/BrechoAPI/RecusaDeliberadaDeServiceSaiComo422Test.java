package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

















































































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecusaDeliberadaDeServiceSaiComo422Test {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    

    








    @Test
    void sitio4_postComTamanhoForaDoRegimeSaiComo422ComAMensagem() throws Exception {
        Cena c = cena(CategorySizeType.ALPHA);

        mockMvc.perform(post("/seller/products")
                        .with(user(c.dona))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoCreate(c.categoryId, "42")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message")
                        .value("tamanho '42' não é válido para a categoria (regime ALPHA)"));
    }

    

    








    @Test
    void sitio4_putComTamanhoForaDoRegimeSaiComo422ComAMensagem() throws Exception {
        Cena c = cena(CategorySizeType.ALPHA);
        Product peca = productRepository.save(Product.create(
                userRepository.findById(c.dona.getId()).orElseThrow(),
                categoryRepository.findById(c.categoryId).orElseThrow(),
                "Peça válida", "desc", "Marca", "M", null, "azul",
                ProductCondition.GOOD, new BigDecimal("70.00")));

        mockMvc.perform(put("/seller/products/{id}", peca.getId())
                        .with(user(c.dona))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoUpdate(c.categoryId, "42")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message")
                        .value("tamanho '42' não é válido para a categoria (regime ALPHA)"));
    }

    

    







    @Test
    void sitio3_uploadSemArquivo_respostaObservavelDaRotaMultipart() throws Exception {
        Cena c = cena(CategorySizeType.ALPHA);
        Product peca = productRepository.save(Product.create(
                userRepository.findById(c.dona.getId()).orElseThrow(),
                categoryRepository.findById(c.categoryId).orElseThrow(),
                "Peça válida", "desc", "Marca", "M", null, "azul",
                ProductCondition.GOOD, new BigDecimal("70.00")));

        MvcResult resposta = mockMvc.perform(multipart("/seller/products/{id}/photos", peca.getId())
                        .with(user(c.dona)))
                .andReturn();

        
        
        
        assertThat(resposta.getResponse().getStatus())
                .as("sítio 3: a rota multipart sem a parte 'files' é barrada antes do service; "
                        + "corpo: %s", resposta.getResponse().getContentAsString())
                .isEqualTo(400);
    }

    

    record Cena(CustomUserDetails dona, UUID categoryId) {}

    private Cena cena(CategorySizeType regime) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-dv5-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-dv5-" + m, regime));
        return new Cena(new CustomUserDetails(dona), categoria.getId());
    }

    private String corpoCreate(UUID categoryId, String size) {
        return """
                {"title":"Peça","description":"descrição","brand":"Marca",
                 "size":"%s","color":"azul","condition":"GOOD","price":50.00,
                 "categoryId":"%s"}""".formatted(size, categoryId);
    }

    private String corpoUpdate(UUID categoryId, String size) {
        return """
                {"title":"Peça","description":"descrição","brand":"Marca",
                 "color":"azul","size":"%s","condition":"GOOD","price":50.00,
                 "categoryId":"%s"}""".formatted(size, categoryId);
    }
}
