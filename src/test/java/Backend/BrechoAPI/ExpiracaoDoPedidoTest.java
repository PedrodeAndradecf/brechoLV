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
import Backend.BrechoAPI.service.OrderExpiryService;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;




















































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)





@TestPropertySource(properties = "checkout.order-ttl=PT30M")
class ExpiracaoDoPedidoTest {

    




























































































































































































































































































































































    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    


    private static final Duration PRAZO = Duration.ofMinutes(30);

    




    private static final Duration ANTES_DO_PRAZO = PRAZO.minusMinutes(1);
    private static final Duration DEPOIS_DO_PRAZO = PRAZO.plusMinutes(1);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired OrderExpiryService orderExpiryService;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    

    


































    @Test
    void oServicoDeExpiracaoExisteComAAssinaturaDoPlano() {
        Method metodo = metodoDeExpiracao(OrderExpiryService.class);

        assertThat(Modifier.isPublic(metodo.getModifiers()))
                .as("expireDueOf precisa ser público: o chamador de hoje é o checkout, e o de "
                        + "amanhã é o job da Fase 4")
                .isTrue();
        assertThat(metodo.getReturnType())
                .as("o retorno é a CONTAGEM de pedidos fechados — void deixaria a segunda "
                        + "chamada do teste 3 indistinguível da primeira")
                .isEqualTo(int.class);

        Class<?> alvo = AopProxyUtils.ultimateTargetClass(orderExpiryService);
        Method noAlvo = metodoDeExpiracao(alvo);
        boolean transacional = AnnotatedElementUtils.hasAnnotation(noAlvo, Transactional.class)
                || AnnotatedElementUtils.hasAnnotation(alvo, Transactional.class);

        assertThat(transacional)
                .as("@Transactional está no bloco de assinatura da tarefa; sem ela a escrita "
                        + "só funciona por acidente do repositório e o passo 1 do checkout "
                        + "deixa de participar da transação do pedido")
                .isTrue();
    }

    

    


























    @Test
    void pedidoVencidoEhFechado() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        Instant t0 = clock.instant();
        UUID pedido = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        int fechados = expireDueOf(cliente.userId(), t0.plus(DEPOIS_DO_PRAZO));

        assertThat(fechados)
                .as("um pedido vencido deste cliente foi fechado")
                .isEqualTo(1);
        assertThat(statusDoPedido(pedido))
                .as("a transição EXPIRED é a razão de o valor existir no enum e no "
                        + "ck_orders_status — sem escritor, a Regra 1 o proibiria")
                .isEqualTo("EXPIRED");
    }

    












































    @Test
    void pedidoVencidoDestravaOCliente() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        Instant t0 = clock.instant();
        UUID pedidoVelho = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        expireDueOf(cliente.userId(), t0.plus(DEPOIS_DO_PRAZO));

        
        
        adicionarAoCarrinho(cliente.token(), criarPeca("130.00"));

        MvcResult novo = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(idDoPedido(novo))
                .as("é um pedido NOVO, não o vencido devolvido de novo")
                .isNotEqualTo(pedidoVelho);
        assertThat(pedidosDe(cliente))
                .as("dois pedidos: o vencido (EXPIRED) e o novo (AWAITING_PAYMENT)")
                .isEqualTo(2);
    }

    













    @Test
    void antesDoPrazoNadaEhFechado() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        Instant t0 = clock.instant();
        UUID pedido = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());
        Timestamp tocadoAntes = updatedAtDoPedido(pedido);

        int fechados = expireDueOf(cliente.userId(), t0.plus(ANTES_DO_PRAZO));

        assertThat(fechados)
                .as("dentro do prazo não há o que fechar")
                .isZero();
        assertThat(statusDoPedido(pedido))
                .as("o pedido do cliente que ainda pode pagar não pode ser fechado nas costas dele")
                .isEqualTo("AWAITING_PAYMENT");
        assertThat(updatedAtDoPedido(pedido))
                .as("nem a linha foi tocada — um UPDATE que reescrevesse o mesmo status "
                        + "continuaria sendo escrita indevida")
                .isEqualTo(tocadoAntes);

        adicionarAoCarrinho(cliente.token(), criarPeca("130.00"));

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_OPEN"));
    }

    

    













    @Test
    void expirarDuasVezesNaoMudaNada() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        Instant t0 = clock.instant();
        UUID pedido = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        Instant depois = t0.plus(DEPOIS_DO_PRAZO);

        assertThat(expireDueOf(cliente.userId(), depois)).isEqualTo(1);
        Timestamp tocadoNaPrimeira = updatedAtDoPedido(pedido);

        assertThat(expireDueOf(cliente.userId(), depois))
                .as("a segunda chamada não acha pedido aberto vencido — não há o que fechar")
                .isZero();
        assertThat(updatedAtDoPedido(pedido))
                .as("e não regravou a linha: idempotência de verdade é a linha intacta, não o "
                        + "número zero de retorno")
                .isEqualTo(tocadoNaPrimeira);
        assertThat(statusDoPedido(pedido)).isEqualTo("EXPIRED");
        assertThat(pedidosDe(cliente))
                .as("e não criou nada")
                .isEqualTo(1);
    }

    

    













    @Test
    void expiracaoNaoMexeNoCarrinho() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));
        adicionarAoCarrinho(cliente.token(), criarPeca("130.00"));

        Instant t0 = clock.instant();
        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        String carrinhoAntes = carrinhoDe(cliente);
        Integer quantasPecas = ler(carrinhoAntes, "$.items.length()");
        assertThat(quantasPecas)
                .as("guarda do próprio teste: sem as duas peças no carrinho, comparar antes e "
                        + "depois compararia dois vazios e passaria por nada")
                .isEqualTo(2);

        expireDueOf(cliente.userId(), t0.plus(DEPOIS_DO_PRAZO));

        assertThat(carrinhoDe(cliente))
                .as("o carrinho depois da expiração é byte a byte o mesmo de antes")
                .isEqualTo(carrinhoAntes);
    }

    













    @Test
    void expiracaoNaoMexeEmProducts() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        Instant t0 = clock.instant();
        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        List<Map<String, Object>> antes = fotoDeProducts();
        assertThat(antes)
                .as("guarda do próprio teste: com a tabela vazia esta comparação é vácuo")
                .isNotEmpty();

        expireDueOf(cliente.userId(), t0.plus(DEPOIS_DO_PRAZO));

        assertThat(fotoDeProducts())
                .as("nenhuma linha de products alterada — não há estoque para devolver porque "
                        + "nada foi tirado")
                .isEqualTo(antes);
    }

    

    























    @Test
    void oPrazoEfetivoEhOConfigurado() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        Instant t0 = clock.instant();
        UUID pedido = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        
        assertThat(expireDueOf(cliente.userId(), t0.plus(ANTES_DO_PRAZO)))
                .as("a %s minutos do checkout, com prazo de %s, o pedido ainda vale",
                        ANTES_DO_PRAZO.toMinutes(), PRAZO)
                .isZero();
        assertThat(statusDoPedido(pedido)).isEqualTo("AWAITING_PAYMENT");

        
        assertThat(expireDueOf(cliente.userId(), t0.plus(DEPOIS_DO_PRAZO)))
                .as("a %s minutos do checkout, com prazo de %s, o pedido venceu",
                        DEPOIS_DO_PRAZO.toMinutes(), PRAZO)
                .isEqualTo(1);
        assertThat(statusDoPedido(pedido)).isEqualTo("EXPIRED");

        
        assertThat(expiresAtDoPedido(pedido))
                .as("expires_at = instante do checkout + checkout.order-ttl (%s). O intervalo "
                        + "de um minuto é o tempo entre a leitura do Clock e o POST, não folga "
                        + "de asserção", PRAZO)
                .isBetween(t0.plus(PRAZO), t0.plus(PRAZO).plusSeconds(60));
    }

    

    






























































    @Test
    void pedidoVencidoNoRelogioNaoDerrubaOCheckoutSeguinte() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        UUID pedidoVelho = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        
        
        
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofHours(1))), pedidoVelho);
        assertThat(statusDoPedido(pedidoVelho))
                .as("guarda: o vencido tem que chegar ao próximo checkout AINDA aberto, senão "
                        + "o passo 1 não é exercitado e este teste vira o do EscritaDiretaTest")
                .isEqualTo("AWAITING_PAYMENT");

        adicionarAoCarrinho(cliente.token(), criarPeca("130.00"));

        MvcResult novo = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();

        assertThat(statusDoPedido(pedidoVelho))
                .as("o próprio checkout fechou o vencido — é o único escritor de EXPIRED nesta "
                        + "fase (decisão 10: o fato tem um leitor só, e ele o produz)")
                .isEqualTo("EXPIRED");
        assertThat(idDoPedido(novo)).isNotEqualTo(pedidoVelho);
        assertThat(pedidosDe(cliente)).isEqualTo(2);
    }

    

    





















    @Test
    void expiracaoDeUmClienteNaoFechaPedidoDeOutro() throws Exception {
        Cliente umCliente = novoClienteComEndereco();
        adicionarAoCarrinho(umCliente.token(), criarPeca("70.00"));
        Instant t0 = clock.instant();
        UUID pedidoDeUm = idDoPedido(mockMvc.perform(checkout(umCliente))
                .andExpect(status().isCreated())
                .andReturn());

        Cliente outroCliente = novoClienteComEndereco();
        adicionarAoCarrinho(outroCliente.token(), criarPeca("90.00"));
        UUID pedidoDoOutro = idDoPedido(mockMvc.perform(checkout(outroCliente))
                .andExpect(status().isCreated())
                .andReturn());

        
        
        int fechados = expireDueOf(umCliente.userId(), t0.plus(DEPOIS_DO_PRAZO));

        assertThat(fechados)
                .as("um pedido fechado, não dois: o do cliente pedido")
                .isEqualTo(1);
        assertThat(statusDoPedido(pedidoDeUm)).isEqualTo("EXPIRED");
        assertThat(statusDoPedido(pedidoDoOutro))
                .as("o pedido do outro cliente estava IGUALMENTE vencido neste instante e "
                        + "mesmo assim não podia ser tocado — quem decide é o userId, não o prazo")
                .isEqualTo("AWAITING_PAYMENT");
    }

    





























































    

    















    

    private int expireDueOf(UUID userId, Instant now) {
        return orderExpiryService.expireDueOf(userId, now);
    }

    




    private static Method metodoDeExpiracao(Class<?> tipo) {
        try {
            return tipo.getMethod("expireDueOf", UUID.class, Instant.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("expireDueOf(UUID, Instant) não está em " + tipo.getName()
                    + " — se o método foi renomeado, a chamada tipada em expireDueOf() desta "
                    + "classe já não compila: corrija lá e aqui", e);
        }
    }

    

    private static final String TELEFONE_DO_CLIENTE = "11987654321";

    



    private static final UfBrasileira UF = UfBrasileira.SP;

    private record Cliente(String token, UUID addressId, UUID userId) {}

    private RequestBuilder checkout(Cliente cliente) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + cliente.addressId() + "\"}");
    }

    private String carrinhoDe(Cliente cliente) throws Exception {
        return mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private UUID idDoPedido(MvcResult resultado) throws Exception {
        return UUID.fromString(ler(resultado.getResponse().getContentAsString(), "$.id"));
    }

    private String statusDoPedido(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private Timestamp updatedAtDoPedido(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT updated_at FROM orders WHERE id = ?", Timestamp.class, orderId);
    }

    private Instant expiresAtDoPedido(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT expires_at FROM orders WHERE id = ?", Timestamp.class, orderId).toInstant();
    }

    private int pedidosDe(Cliente cliente) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, cliente.userId());
        return total == null ? 0 : total;
    }

    private List<Map<String, Object>> fotoDeProducts() {
        return jdbc.queryForList("SELECT id, updated_at, deleted_at FROM products ORDER BY id");
    }

    





    private static <T> T ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

    

    private Cliente novoClienteComEndereco() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "expiracao-" + m + "@teste.local";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"%s","password":"senha-forte-123",
                                 "phone":"%s"}"""
                                .formatted(m, email, TELEFONE_DO_CLIENTE)))
                .andExpect(status().isCreated());

        String resposta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = ler(resposta, "$.accessToken");

        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"Rua Um","number":"100",
                                 "complement":null,"district":"Centro","city":"São Paulo",
                                 "uf":"%s"}""".formatted(UF.name())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);

        return new Cliente(token, UUID.fromString(ler(endereco, "$.id")), userId);
    }

    


    private UUID criarPeca(String preco) {
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
}
