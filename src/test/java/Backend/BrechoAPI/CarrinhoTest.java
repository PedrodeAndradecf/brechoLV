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
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;





























@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CarrinhoTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
        
        
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    





    @Test
    void itemAdicionadoSobrevivePeloLoginDeNovo() throws Exception {
        String email = novoEmail();
        registrar(email);
        String primeiroToken = login(email);

        UUID productId = criarPeca().productId();

        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primeiroToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(productId)))
                .andExpect(status().isOk());

        
        String segundoToken = login(email);

        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + segundoToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()));
    }

    
    @Test
    void adicionarAMesmaPecaDuasVezesNaoDuplica() throws Exception {
        String email = novoEmail();
        registrar(email);
        String token = login(email);

        UUID productId = criarPeca().productId();

        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(productId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    




    @Test
    void adicionarPecaArquivadaResponde404ComCodigoDePeca() throws Exception {
        String email = novoEmail();
        registrar(email);
        String token = login(email);

        Peca peca = criarPeca();

        mockMvc.perform(delete("/seller/products/{id}", peca.productId())
                        .with(user(peca.dona())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(peca.productId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
    }

    





    @Test
    void vendedoraSemCarrinhoRecebeListaVaziaNunca404() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User vendedora = userRepository.save(User.createWithRole(
                "Vendedora " + m, "vendedora-" + m + "@teste.local", "hash", Role.SELLER));

        mockMvc.perform(get("/me/cart").with(user(new CustomUserDetails(vendedora))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    




    @Test
    void vendedoraSemCarrinhoTentaAdicionarItemRecebe404ComCodigoDeCarrinho() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User vendedora = userRepository.save(User.createWithRole(
                "Vendedora " + m, "vendedora-" + m + "@teste.local", "hash", Role.SELLER));

        mockMvc.perform(post("/me/cart/items")
                        .with(user(new CustomUserDetails(vendedora)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CART_NOT_FOUND"));
    }

    










    @Test
    void pecaArquivadaContinuaNoCarrinhoMarcadaComoIndisponivel() throws Exception {
        String email = novoEmail();
        registrar(email);
        String token = login(email);

        
        Peca peca = criarPeca();

        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(peca.productId())))
                .andExpect(status().isOk());

        
        mockMvc.perform(delete("/seller/products/{id}", peca.productId())
                        .with(user(peca.dona())))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(peca.productId().toString()))
                .andExpect(jsonPath("$.items[0].archived").value(true));
    }

    



























    @Test
    void leituraDeCarrinhoNaoEscalaConsultasDeProdutoComNumeroDeItens() throws Exception {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sessionFactory.getStatistics();
        stats.setStatisticsEnabled(true);

        
        String emailUm = novoEmail();
        registrar(emailUm);
        String tokenUm = login(emailUm);
        adicionarAoCarrinho(tokenUm, criarPeca().productId());

        stats.clear();
        mockMvc.perform(get("/me/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUm))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        long consultasComUmItem = stats.getPrepareStatementCount();

        
        
        String emailTres = novoEmail();
        registrar(emailTres);
        String tokenTres = login(emailTres);
        adicionarAoCarrinho(tokenTres, criarPeca().productId());
        adicionarAoCarrinho(tokenTres, criarPeca().productId());
        adicionarAoCarrinho(tokenTres, criarPeca().productId());

        stats.clear();
        mockMvc.perform(get("/me/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenTres))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));
        long consultasComTresItens = stats.getPrepareStatementCount();

        long itensExtras = 2; 
        long diferencaDeConsultas = consultasComTresItens - consultasComUmItem;

        System.out.println("[T1 N+1 CartService] SELECTs (getPrepareStatementCount) — "
                + "carrinho 1 item: " + consultasComUmItem
                + " | carrinho 3 itens: " + consultasComTresItens
                + " | diferença: " + diferencaDeConsultas);

        assertTrue(diferencaDeConsultas < itensExtras,
                "contagem de consultas escalou proporcionalmente ao número de itens (N+1 em "
                        + "CartService.toResponse): GET /me/cart com 1 item disparou "
                        + consultasComUmItem + " consultas, com 3 itens disparou "
                        + consultasComTresItens + " (diferença = " + diferencaDeConsultas
                        + ", igual aos " + itensExtras + " itens extras); esperado uma "
                        + "diferença constante (0), não proporcional aos itens extras");
    }

    











    @Test
    void removerItemEspecificoTiraSoEleDoCarrinho() throws Exception {
        String email = novoEmail();
        registrar(email);
        String token = login(email);

        UUID productIdRemovido = criarPeca().productId();
        UUID productIdMantido = criarPeca().productId();

        adicionarAoCarrinho(token, productIdRemovido);
        adicionarAoCarrinho(token, productIdMantido);

        mockMvc.perform(delete("/me/cart/items/{productId}", productIdRemovido)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        
        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productIdMantido.toString()));
    }

    







    @Test
    void removerItemDuasVezesEhIdempotente() throws Exception {
        String email = novoEmail();
        registrar(email);
        String token = login(email);

        UUID productId = criarPeca().productId();
        adicionarAoCarrinho(token, productId);

        mockMvc.perform(delete("/me/cart/items/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/me/cart/items/{productId}", productId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    









    @Test
    void removerItemDeCarrinhoInexistenteRecebe204SemErro() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User vendedora = userRepository.save(User.createWithRole(
                "Vendedora " + m, "vendedora-" + m + "@teste.local", "hash", Role.SELLER));

        mockMvc.perform(delete("/me/cart/items/{productId}", UUID.randomUUID())
                        .with(user(new CustomUserDetails(vendedora))))
                .andExpect(status().isNoContent());
    }

    












    @Test
    void esvaziarCarrinhoRemoveTodosOsItens() throws Exception {
        String email = novoEmail();
        registrar(email);
        String token = login(email);

        adicionarAoCarrinho(token, criarPeca().productId());
        adicionarAoCarrinho(token, criarPeca().productId());
        adicionarAoCarrinho(token, criarPeca().productId());

        mockMvc.perform(delete("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        
        
        mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.cartId").exists());
    }

    





    @Test
    void esvaziarCarrinhoDuasVezesEhIdempotente() throws Exception {
        String email = novoEmail();
        registrar(email);
        String token = login(email);

        adicionarAoCarrinho(token, criarPeca().productId());
        adicionarAoCarrinho(token, criarPeca().productId());

        mockMvc.perform(delete("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    




    @Test
    void esvaziarCarrinhoDeCarrinhoInexistenteRecebe204SemErro() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User vendedora = userRepository.save(User.createWithRole(
                "Vendedora " + m, "vendedora-" + m + "@teste.local", "hash", Role.SELLER));

        mockMvc.perform(delete("/me/cart/items")
                        .with(user(new CustomUserDetails(vendedora))))
                .andExpect(status().isNoContent());
    }

    private void adicionarAoCarrinho(String token, UUID productId) throws Exception {
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoAddItem(productId)))
                .andExpect(status().isOk());
    }

    record Peca(UUID productId, CustomUserDetails dona) {}

    

    private Peca criarPeca() {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        Product produto = productRepository.save(Product.create(dona, categoria, "Peça " + m,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal("70.00")));

        return new Peca(produto.getId(), new CustomUserDetails(dona));
    }

    private void registrar(String email) throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoRegistro(email)))
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

    private String novoEmail() {
        return "cart-" + UUID.randomUUID() + "@teste.local";
    }

    private String corpoRegistro(String email) {
        return """
                {"name":"Cliente Carrinho","email":"%s","password":"senha-forte-123","phone":"11987654321"}"""
                .formatted(email);
    }

    private String corpoAddItem(UUID productId) {
        return """
                {"productId":"%s"}""".formatted(productId);
    }
}
