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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
















@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductConcorrenciaTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void duasEdicoesConcorrentesUmaPassaAOutraRecebeConflito() throws Exception {
        UUID productId = seedProduct();

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CountDownLatch ambosLeram = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            Future<String> resultadoA = pool.submit(tentarEditar(tx, productId, "Editado por A", ambosLeram));
            Future<String> resultadoB = pool.submit(tentarEditar(tx, productId, "Editado por B", ambosLeram));

            String a = resultadoA.get(10, TimeUnit.SECONDS);
            String b = resultadoB.get(10, TimeUnit.SECONDS);

            
            
            
            assertThat(java.util.List.of(a, b))
                    .as("uma edição deveria comitar e a outra receber o conflito de versão")
                    .containsExactlyInAnyOrder("ok", "conflito");
        } finally {
            pool.shutdownNow();
        }

        Product atual = productRepository.findById(productId).orElseThrow();
        assertThat(atual.getTitle()).isIn("Editado por A", "Editado por B");
    }

    




    private Callable<String> tentarEditar(TransactionTemplate tx, UUID productId, String novoTitulo,
                                          CountDownLatch ambosLeram) {
        return () -> {
            try {
                tx.executeWithoutResult(status -> {
                    Product product = productRepository.findById(productId).orElseThrow();

                    ambosLeram.countDown();
                    try {
                        if (!ambosLeram.await(5, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("a outra thread não chegou a tempo");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }

                    product.updateDetails(novoTitulo, product.getDescription(), product.getBrand(),
                            product.getColor(), product.getCondition());
                });
                return "ok";
            } catch (ObjectOptimisticLockingFailureException e) {
                return "conflito";
            }
        };
    }

    private UUID seedProduct() {
        User seller = userRepository.save(
                User.createWithRole("Vendedora IT", "vendedora-it-" + UUID.randomUUID() + "@teste.local",
                        "hash-nao-usado-neste-teste", Role.SELLER));
        Category category = categoryRepository.save(
                Category.create("Vestidos IT", "vestidos-it-" + UUID.randomUUID(), CategorySizeType.ALPHA));
        Product product = Product.create(seller, category, "Vestido original", "descrição original", "Zara",
                "M", null, "azul", ProductCondition.GOOD, new BigDecimal("89.90"));
        return productRepository.save(product).getId();
    }
}
