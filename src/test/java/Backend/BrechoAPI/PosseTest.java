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

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;






















@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PosseTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    @Test
    void donaEditaAPropriaPeca() throws Exception {
        Cena c = cena();

        mockMvc.perform(put("/seller/products/{id}", c.productId)
                        .with(user(c.dona))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(c.categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Editado pela dona"));
    }

    @Test
    void outraVendedoraNaoEditaPecaAlheia() throws Exception {
        Cena c = cena();

        mockMvc.perform(put("/seller/products/{id}", c.productId)
                        .with(user(c.outra))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo(c.categoryId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void donaArquivaAPropriaPeca() throws Exception {
        Cena c = cena();

        mockMvc.perform(delete("/seller/products/{id}", c.productId).with(user(c.dona)))
                .andExpect(status().isNoContent());
    }

    



    @Test
    void outraVendedoraNaoArquivaPecaAlheia() throws Exception {
        Cena c = cena();

        mockMvc.perform(delete("/seller/products/{id}", c.productId).with(user(c.outra)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/products/{id}", c.productId))
                .andExpect(status().isOk());
    }

    





    @Test
    void pecaInexistenteRespondeForbiddenNaoNotFound() throws Exception {
        Cena c = cena();

        mockMvc.perform(delete("/seller/products/{id}", UUID.randomUUID()).with(user(c.dona)))
                .andExpect(status().isForbidden());
    }

    record Cena(CustomUserDetails dona, CustomUserDetails outra, UUID productId, UUID categoryId) {}

    private Cena cena() {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-" + m + "@teste.local", "hash", Role.SELLER));
        User outra = userRepository.save(User.createWithRole(
                "Outra " + m, "outra-" + m + "@teste.local", "hash", Role.SELLER));
        Category cat = categoryRepository.save(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        Product p = productRepository.save(Product.create(dona, cat, "Peça da dona", "desc",
                "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal("70.00")));

        return new Cena(new CustomUserDetails(dona), new CustomUserDetails(outra),
                p.getId(), cat.getId());
    }

    private String corpo(UUID categoryId) {
        return """
                {"title":"Editado pela dona","description":"nova descrição",
                 "brand":"Marca","color":"azul","size":"G",
                 "condition":"GOOD","price":99.90,"categoryId":"%s"}""".formatted(categoryId);
    }
}
