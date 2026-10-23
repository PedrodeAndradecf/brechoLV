package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


































































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EscalaDeConsultasDoPedidoTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
        
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired EntityManagerFactory entityManagerFactory;

    

    private static final long ITENS_EXTRAS = 2;

    






    @Test
    void criacaoDoPedido_controle() throws Exception {
        Statistics stats = stats();

        Cliente um = clienteComCarrinho(1);
        stats.clear();
        mockMvc.perform(checkout(um)).andExpect(status().isCreated());
        long criacaoUm = stats.getPrepareStatementCount();

        Cliente tres = clienteComCarrinho(3);
        stats.clear();
        mockMvc.perform(checkout(tres)).andExpect(status().isCreated());
        long criacaoTres = stats.getPrepareStatementCount();

        long delta = criacaoTres - criacaoUm;
        System.out.println("[DV4 CONTROLE criacao] SELECTs — 1 item: " + criacaoUm
                + " | 3 itens: " + criacaoTres + " | delta: " + delta);

        assertThat(delta)
                .as("CONTROLE: a criacao do pedido escala com o numero de itens — delta medido 4 "
                        + "(checagem A por item + inserts de OrderItem nao-batcheados). 1 item=%d, "
                        + "3 itens=%d. Correcao e tarefa futura nomeada (spec DV4 §7 item 3); esta "
                        + "assercao so prende o numero real medido", criacaoUm, criacaoTres)
                .isEqualTo(2 * ITENS_EXTRAS);
    }

    








    @Test
    void retomadaDoPedido() throws Exception {
        Statistics stats = stats();

        Cliente um = clienteComCarrinho(1);
        mockMvc.perform(checkout(um)).andExpect(status().isCreated()); 
        stats.clear();
        mockMvc.perform(checkout(um)).andExpect(status().isOk());      
        long retomadaUm = stats.getPrepareStatementCount();

        Cliente tres = clienteComCarrinho(3);
        mockMvc.perform(checkout(tres)).andExpect(status().isCreated());
        stats.clear();
        mockMvc.perform(checkout(tres)).andExpect(status().isOk());
        long retomadaTres = stats.getPrepareStatementCount();

        long delta = retomadaTres - retomadaUm;
        System.out.println("[DV4 retomada] SELECTs — 1 item: " + retomadaUm
                + " | 3 itens: " + retomadaTres + " | delta: " + delta);

        assertThat(delta)
                .as("a retomada escala +1 por item: delta medido 2, pela checagem A por item em "
                        + "conferirDisponibilidadeDoPedidoAberto (ADR 0003 §4), NAO pelo montador "
                        + "getProduct().getId() (que e livre). 1 item=%d, 3 itens=%d. Correcao e "
                        + "tarefa futura nomeada (spec DV4 §7 item 3)", retomadaUm, retomadaTres)
                .isEqualTo(ITENS_EXTRAS);
    }

    









    @Test
    void leituraGetDoPedido() throws Exception {
        Statistics stats = stats();

        Cliente um = clienteComCarrinho(1);
        UUID pedidoUm = idDoPedido(mockMvc.perform(checkout(um))
                .andExpect(status().isCreated()).andReturn());
        stats.clear();
        mockMvc.perform(lerPedido(um, pedidoUm)).andExpect(status().isOk());
        long getUm = stats.getPrepareStatementCount();

        Cliente tres = clienteComCarrinho(3);
        UUID pedidoTres = idDoPedido(mockMvc.perform(checkout(tres))
                .andExpect(status().isCreated()).andReturn());
        stats.clear();
        mockMvc.perform(lerPedido(tres, pedidoTres)).andExpect(status().isOk());
        long getTres = stats.getPrepareStatementCount();

        long delta = getTres - getUm;
        System.out.println("[DV4 GET /me/orders/{id}] SELECTs — 1 item: " + getUm
                + " | 3 itens: " + getTres + " | delta: " + delta);

        assertThat(delta)
                .as("o GET /me/orders/{id} NAO escala consultas com o numero de itens "
                        + "(getProduct().getId() e livre no proxy LAZY): delta esperado constante "
                        + "(0). 1 item=%d, 3 itens=%d. Delta > 0 aqui e N+1 no montador de "
                        + "OrderService.findOwn (a hipotese de 08/09 confirmada)", getUm, getTres)
                .isEqualTo(0);
    }

    

    private Statistics stats() {
        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics s = sf.getStatistics();
        s.setStatisticsEnabled(true);
        return s;
    }

    private static final String TELEFONE = "11987654321";

    private record Cliente(String token, UUID addressId) {}

    private RequestBuilder checkout(Cliente c) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + c.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + c.addressId() + "\"}");
    }

    private RequestBuilder lerPedido(Cliente c, UUID orderId) {
        return get("/me/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + c.token());
    }

    private Cliente clienteComCarrinho(int nItens) throws Exception {
        Cliente c = novoClienteComEndereco(UfBrasileira.SP);
        for (int i = 0; i < nItens; i++) {
            adicionarAoCarrinho(c.token(), criarPecaId("50.00"));
        }
        return c;
    }

    private Cliente novoClienteComEndereco(UfBrasileira uf) throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "escala-" + m + "@teste.local";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"%s","password":"senha-forte-123",
                                 "phone":"%s"}""".formatted(m, email, TELEFONE)))
                .andExpect(status().isCreated());

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = ler(login, "$.accessToken");

        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"Rua Um","number":"100",
                                 "complement":null,"district":"Centro","city":"Cidade",
                                 "uf":"%s"}""".formatted(uf.name())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return new Cliente(token, UUID.fromString(ler(endereco, "$.id")));
    }

    private UUID criarPecaId(String preco) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        Product produto = productRepository.save(Product.create(dona, categoria, "Peça " + m,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal(preco)));
        return produto.getId();
    }

    private void adicionarAoCarrinho(String token, UUID productId) throws Exception {
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isOk());
    }

    private static UUID idDoPedido(MvcResult r) throws Exception {
        return UUID.fromString(ler(r.getResponse().getContentAsString(), "$.id"));
    }

    private static <T> T ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);
}
