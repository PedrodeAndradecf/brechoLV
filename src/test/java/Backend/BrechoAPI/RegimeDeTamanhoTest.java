package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.dto.request.ProductCreateRequestDTO;
import Backend.BrechoAPI.dto.request.ProductUpdateRequestDTO;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.service.SellerService;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

















@SpringBootTest
@Import(TestcontainersConfiguration.class)
class RegimeDeTamanhoTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired SellerService sellerService;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    @Test
    void postRecusaTamanhoNumericoEmCategoriaAlpha() {
        Ctx c = ctx(CategorySizeType.ALPHA);

        assertThatThrownBy(() -> sellerService.createProduct(c.sellerId, create(c.categoryId, "42")))
                .isInstanceOf(DomainRuleException.class)
                .hasMessageContaining("42")
                .asInstanceOf(InstanceOfAssertFactories.type(DomainRuleException.class))
                .extracting(DomainRuleException::getCode)
                .isEqualTo(ErrorCode.DOMAIN_RULE_VIOLATION);
    }

    @Test
    void postRecusaTamanhoAlphaEmCategoriaNumerica() {
        Ctx c = ctx(CategorySizeType.NUMERIC);

        assertThatThrownBy(() -> sellerService.createProduct(c.sellerId, create(c.categoryId, "M")))
                .isInstanceOf(DomainRuleException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(DomainRuleException.class))
                .extracting(DomainRuleException::getCode)
                .isEqualTo(ErrorCode.DOMAIN_RULE_VIOLATION);
    }

    @Test
    void postAceitaTamanhoDoRegimeCerto() {
        Ctx c = ctx(CategorySizeType.NUMERIC);

        var criado = sellerService.createProduct(c.sellerId, create(c.categoryId, "38"));

        assertThat(criado.size()).isEqualTo("38");
    }

    
    @Test
    void putRecusaTamanhoForaDoRegimeDaCategoria() {
        Ctx c = ctx(CategorySizeType.ALPHA);
        UUID productId = sellerService.createProduct(c.sellerId, create(c.categoryId, "M")).id();

        assertThatThrownBy(() -> sellerService.updateProduct(productId,
                update(c.categoryId, "42")))
                .isInstanceOf(DomainRuleException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(DomainRuleException.class))
                .extracting(DomainRuleException::getCode)
                .isEqualTo(ErrorCode.DOMAIN_RULE_VIOLATION);
    }

    




    @Test
    void putRecusaTrocaDeCategoriaQueInvalidaOTamanhoAtual() {
        Ctx alpha = ctx(CategorySizeType.ALPHA);
        Category numerica = categoryRepository.save(Category.create(
                "Tênis " + alpha.marker, "tenis-" + alpha.marker, CategorySizeType.NUMERIC));
        UUID productId = sellerService.createProduct(alpha.sellerId, create(alpha.categoryId, "M")).id();

        assertThatThrownBy(() -> sellerService.updateProduct(productId,
                update(numerica.getId(), "M")))
                .isInstanceOf(DomainRuleException.class)
                .asInstanceOf(InstanceOfAssertFactories.type(DomainRuleException.class))
                .extracting(DomainRuleException::getCode)
                .isEqualTo(ErrorCode.DOMAIN_RULE_VIOLATION);
    }

    
    @Test
    void putAplicaOsCamposEnviados() {
        Ctx c = ctx(CategorySizeType.ALPHA);
        UUID productId = sellerService.createProduct(c.sellerId, create(c.categoryId, "M")).id();

        var out = sellerService.updateProduct(productId, new ProductUpdateRequestDTO(
                "Título novo", "Descrição nova", "Marca nova", "verde", "G", "GG-br",
                ProductCondition.NEW_WITH_TAG, new BigDecimal("150.00"), c.categoryId));

        assertThat(out.title()).isEqualTo("Título novo");
        assertThat(out.description()).isEqualTo("Descrição nova");
        assertThat(out.size()).isEqualTo("G");
        assertThat(out.sizeLabel()).isEqualTo("GG-br");
        assertThat(out.price()).isEqualByComparingTo("150.00");

        
        assertThat(productRepository.findById(productId).orElseThrow().getTitle())
                .isEqualTo("Título novo");
    }

    record Ctx(UUID sellerId, UUID categoryId, String marker) {}

    private Ctx ctx(CategorySizeType regime) {
        String marker = UUID.randomUUID().toString().substring(0, 8);
        User seller = userRepository.save(User.createWithRole("V " + marker,
                "v-" + marker + "@teste.local", "hash", Role.SELLER));
        Category category = categoryRepository.save(Category.create(
                "Cat " + marker, "cat-" + marker, regime));
        return new Ctx(seller.getId(), category.getId(), marker);
    }

    private ProductCreateRequestDTO create(UUID categoryId, String size) {
        return new ProductCreateRequestDTO("Peça", "descrição", "Marca", size, null,
                "azul", ProductCondition.GOOD, new BigDecimal("50.00"), categoryId);
    }

    private ProductUpdateRequestDTO update(UUID categoryId, String size) {
        return new ProductUpdateRequestDTO("Peça", "descrição", "Marca", "azul", size, null,
                ProductCondition.GOOD, new BigDecimal("50.00"), categoryId);
    }
}
