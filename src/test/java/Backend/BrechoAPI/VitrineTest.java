package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.ProductPhoto;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductPhotoRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.service.ProductService;
import Backend.BrechoAPI.service.SellerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;





















@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VitrineTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ProductRepository productRepository;
    @Autowired ProductPhotoRepository productPhotoRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;
    @Autowired SellerService sellerService;
    @Autowired ProductService productService;

    








    @Test
    void leituraPublicaSemPrincipalDevolveOsCamposDaProjection() throws Exception {
        Fixture f = seed();

        mockMvc.perform(get("/products").param("categoryId", f.categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + f.productId + "')].title")
                        .value("Vestido vitrine"))
                .andExpect(jsonPath("$.content[?(@.id=='" + f.productId + "')].brand")
                        .value("Zara-" + f.marker))
                .andExpect(jsonPath("$.content[?(@.id=='" + f.productId + "')].size").value("M"))
                .andExpect(jsonPath("$.content[?(@.id=='" + f.productId + "')].color").value("azul"))
                .andExpect(jsonPath("$.content[?(@.id=='" + f.productId + "')].price").value(89.90))
                
                .andExpect(jsonPath("$.content[?(@.id=='" + f.productId + "')].coverPhotoUrl")
                        .isNotEmpty());
    }

    










    @Test
    void paginacaoRespeitaOTamanhoDaPagina() throws Exception {
        Fixture f = seed();

        mockMvc.perform(get("/products")
                        .param("categoryId", f.categoryId.toString())
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/products")
                        .param("categoryId", f.categoryId.toString())
                        .param("size", "1")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    





    @Test
    void filtroDeTamanhoNaoColideComPaginacao() throws Exception {
        Fixture f = seed();

        
        mockMvc.perform(get("/products")
                        .param("categoryId", f.categoryId.toString())
                        .param("size", "1"))
                .andExpect(jsonPath("$.totalElements").value(2));

        
        mockMvc.perform(get("/products")
                        .param("categoryId", f.categoryId.toString())
                        .param("productSize", "M"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].size").value("M"))
                .andExpect(jsonPath("$.size").value(20));

        
        mockMvc.perform(get("/products")
                        .param("categoryId", f.categoryId.toString())
                        .param("productSize", "M")
                        .param("size", "5"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.size").value(5));
    }

    





    @Test
    void arquivadaSomeDaVitrineEDasFacetas() throws Exception {
        Fixture f = seed();
        sellerService.archiveProduct(f.productId);

        mockMvc.perform(get("/products").param("categoryId", f.categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id=='" + f.productId + "')]").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/products/filters").param("categoryId", f.categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brands[?(@=='Zara-" + f.marker + "')]").isEmpty());
    }

    
    @Test
    void arquivadaNaoEAcessivelPorLinkDireto() throws Exception {
        Fixture f = seed();
        sellerService.archiveProduct(f.productId);

        mockMvc.perform(get("/products/{id}", f.productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }


    record Fixture(UUID productId, UUID categoryId, String marker) {}

    
    private Fixture seed() {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        User seller = userRepository.save(User.createWithRole("V " + marker,
                "v-" + marker + "@teste.local", "hash", Role.SELLER));
        Category category = categoryRepository.save(
                Category.create("Vestidos " + marker, "vestidos-" + marker, CategorySizeType.ALPHA));

        Product p1 = productRepository.save(Product.create(seller, category, "Vestido vitrine",
                "desc", "Zara-" + marker, "M", null, "azul",
                ProductCondition.GOOD, new BigDecimal("89.90")));
        productRepository.save(Product.create(seller, category, "Segundo vestido",
                "desc", "Outra-" + marker, "P", null, "verde",
                ProductCondition.GOOD, new BigDecimal("50.00")));

        ProductPhoto photo = ProductPhoto.create(p1, "products/" + p1.getId() + "/capa.webp", 0);
        photo.markAsCover();
        productPhotoRepository.save(photo);

        return new Fixture(p1.getId(), category.getId(), marker);
    }
}
