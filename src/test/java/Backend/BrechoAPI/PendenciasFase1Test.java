package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.ProductPhoto;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.dto.request.ProductUpdateRequestDTO;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductPhotoRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.service.SellerService;
import Backend.BrechoAPI.service.StorageService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;












@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PendenciasFase1Test {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    









    @MockitoBean StorageService storageService;

    @Autowired SellerService sellerService;
    @Autowired ProductRepository productRepository;
    @Autowired ProductPhotoRepository productPhotoRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository userRepository;

    

    
    @Test
    void putEditaMarcaECor() {
        Ctx c = ctx();
        UUID id = criarPeca(c, "Zra", "azlu");

        var out = sellerService.updateProduct(id, new ProductUpdateRequestDTO(
                "Vestido", "descrição", "Zara", "azul", "M", null,
                ProductCondition.GOOD, new BigDecimal("80.00"), c.categoryId));

        assertThat(out.brand()).isEqualTo("Zara");
        assertThat(out.color()).isEqualTo("azul");

        
        Product recarregado = productRepository.findById(id).orElseThrow();
        assertThat(recarregado.getBrand()).isEqualTo("Zara");
        assertThat(recarregado.getColor()).isEqualTo("azul");
    }

    
    @Test
    void marcaECorContinuamObrigatorias() {
        Ctx c = ctx();
        UUID id = criarPeca(c, "Zara", "azul");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        sellerService.updateProduct(id, new ProductUpdateRequestDTO(
                                "Vestido", "descrição", null, "azul", "M", null,
                                ProductCondition.GOOD, new BigDecimal("80.00"), c.categoryId)))
                .isInstanceOf(NullPointerException.class);
    }

    

    



    @Test
    void removerACapaPromoveAProximaFoto() {
        Ctx c = ctx();
        UUID id = criarPeca(c, "Zara", "azul");
        Product p = productRepository.findById(id).orElseThrow();

        ProductPhoto capa = productPhotoRepository.save(ProductPhoto.create(p, "k/capa.webp", 0));
        capa.markAsCover();
        productPhotoRepository.saveAndFlush(capa);
        ProductPhoto segunda = productPhotoRepository.save(ProductPhoto.create(p, "k/dois.webp", 1));
        ProductPhoto terceira = productPhotoRepository.save(ProductPhoto.create(p, "k/tres.webp", 2));
        productPhotoRepository.flush();

        sellerService.deletePhoto(id, capa.getId());

        ProductPhoto novaCapa = productPhotoRepository.findByProductIdAndCoverIsTrue(id).orElseThrow();
        assertThat(novaCapa.getId())
                .as("a de menor position vira capa, não uma qualquer")
                .isEqualTo(segunda.getId());
        assertThat(terceira.getId()).isNotEqualTo(novaCapa.getId());
    }

    
    @Test
    void removerFotoComumNaoTrocaACapa() {
        Ctx c = ctx();
        UUID id = criarPeca(c, "Zara", "azul");
        Product p = productRepository.findById(id).orElseThrow();

        ProductPhoto capa = productPhotoRepository.save(ProductPhoto.create(p, "k/capa.webp", 0));
        capa.markAsCover();
        productPhotoRepository.saveAndFlush(capa);
        ProductPhoto comum = productPhotoRepository.save(ProductPhoto.create(p, "k/dois.webp", 1));
        productPhotoRepository.flush();

        sellerService.deletePhoto(id, comum.getId());

        assertThat(productPhotoRepository.findByProductIdAndCoverIsTrue(id).orElseThrow().getId())
                .isEqualTo(capa.getId());
    }

    
    @Test
    void removerAUnicaFotoDeixaProdutoSemCapaSemErro() {
        Ctx c = ctx();
        UUID id = criarPeca(c, "Zara", "azul");
        Product p = productRepository.findById(id).orElseThrow();

        ProductPhoto unica = productPhotoRepository.save(ProductPhoto.create(p, "k/u.webp", 0));
        unica.markAsCover();
        productPhotoRepository.saveAndFlush(unica);

        sellerService.deletePhoto(id, unica.getId());

        assertThat(productPhotoRepository.findByProductIdAndCoverIsTrue(id)).isEmpty();
        assertThat(productPhotoRepository.findByProductIdOrderByPositionAsc(id)).isEmpty();
    }

    

    




    @Test
    void includeArchivedTrazAArquivadaMarcadaComoTal() {
        Ctx c = ctx();
        UUID viva = criarPeca(c, "Zara", "azul");
        UUID arquivada = criarPeca(c, "Nike", "preto");
        sellerService.archiveProduct(arquivada);

        var lista = sellerService.listOwnProducts(c.sellerId, pagina(), true);

        assertThat(lista.content()).hasSize(2);
        assertThat(lista.content()).filteredOn(card -> card.id().equals(arquivada))
                .singleElement()
                .satisfies(card -> assertThat(card.archived())
                        .as("aparece — e aparece MARCADA")
                        .isTrue());
        assertThat(lista.content()).filteredOn(card -> card.id().equals(viva))
                .singleElement()
                .satisfies(card -> assertThat(card.archived()).isFalse());
    }

    
    @Test
    void semIncludeArchivedAArquivadaNaoAparece() {
        Ctx c = ctx();
        UUID viva = criarPeca(c, "Zara", "azul");
        UUID arquivada = criarPeca(c, "Nike", "preto");
        sellerService.archiveProduct(arquivada);

        var lista = sellerService.listOwnProducts(c.sellerId, pagina(), false);

        assertThat(lista.content()).hasSize(1);
        assertThat(lista.content().getFirst().id()).isEqualTo(viva);
        assertThat(lista.content().getFirst().archived()).isFalse();
    }

    

    record Ctx(UUID sellerId, UUID categoryId) {}

    private PageRequest pagina() {
        return PageRequest.of(0, 20, Sort.by("createdAt"));
    }

    private Ctx ctx() {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User seller = userRepository.save(User.createWithRole(
                "V " + m, "v-" + m + "@teste.local", "hash", Role.SELLER));
        Category cat = categoryRepository.save(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        return new Ctx(seller.getId(), cat.getId());
    }

    private UUID criarPeca(Ctx c, String brand, String color) {
        User seller = userRepository.findById(c.sellerId).orElseThrow();
        Category cat = categoryRepository.findById(c.categoryId).orElseThrow();
        return productRepository.save(Product.create(seller, cat, "Peça", "desc", brand,
                "M", null, color, ProductCondition.GOOD, new BigDecimal("50.00"))).getId();
    }
}
