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
import Backend.BrechoAPI.security.JwtService;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


































abstract class ApoioDeCobrancaHttp {

    

    static final Duration X = Duration.ofMinutes(15);
    static final Duration VELHA = Duration.ofHours(1);

    static final String TELEFONE_DO_CLIENTE = "11987654321";

    static final Duration TETO_DE_ESPERA = Duration.ofSeconds(20);

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired Clock clock;
    @Autowired PortaDeCobrancaControlavel porta;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired JwtService jwtService;

    private final List<ExecutorService> pools = new ArrayList<>();

    



    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @BeforeEach
    void portaDeFabrica() {
        porta.reiniciar();
    }

    @AfterEach
    void soltarTudo() {
        porta.liberar();
        pools.forEach(ExecutorService::shutdownNow);
        pools.clear();
    }

    

    

    static final class AmbienteNaoRespondeu extends RuntimeException {
        AmbienteNaoRespondeu(String mensagem) {
            super("ERRO DE AMBIENTE (não é asserção): " + mensagem);
        }
    }

    static void aguardar(BooleanSupplier condicao, Duration teto, String oQueEsperava) {
        long limite = System.nanoTime() + teto.toNanos();
        while (!condicao.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AmbienteNaoRespondeu(oQueEsperava + " — teto de " + teto.toSeconds()
                        + " s estourado");
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AmbienteNaoRespondeu("interrompido esperando: " + oQueEsperava);
            }
        }
    }

    <T> Future<T> emParalelo(Callable<T> tarefa) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pools.add(pool);
        return pool.submit(tarefa);
    }

    static <T> T colher(Future<T> futuro, String oQue) throws Exception {
        try {
            return futuro.get(TETO_DE_ESPERA.toSeconds(), TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new AmbienteNaoRespondeu(oQue + " não terminou em " + TETO_DE_ESPERA.toSeconds()
                    + " s");
        }
    }

    

    Set<String> colunasDePayments() {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'payments'
                """, String.class));
    }

    
    void exigirV4(String paraQue) {
        assertThat(colunasDePayments())
                .as("A V4 AINDA NÃO EXISTE (ADR 0016 §3.8): payments não tem status, amount e "
                        + "order_nsu. Sem elas não há como " + paraQue + ". Vermelho de produto "
                        + "da DV0-c — quem o fecha é a DV0-c-impl")
                .contains("status", "amount", "order_nsu");
    }

    

    List<Map<String, Object>> tentativasDe(UUID orderId) {
        return jdbc.queryForList(
                "SELECT * FROM payments WHERE order_id = ? ORDER BY created_at, id", orderId);
    }

    Map<String, Object> tentativa(UUID paymentId) {
        return jdbc.queryForMap("SELECT * FROM payments WHERE id = ?", paymentId);
    }

    

    List<Map<String, Object>> vivasDe(UUID orderId) {
        return tentativasDe(orderId).stream()
                .filter(l -> !"NOT_CREATED".equals(l.get("status")))
                .toList();
    }

    Map<String, Object> ultimaTentativaDe(UUID orderId) {
        List<Map<String, Object>> todas = tentativasDe(orderId);
        assertThat(todas)
                .as("o pedido %s tem de ter ao menos uma tentativa de cobrança em payments", orderId)
                .isNotEmpty();
        return todas.get(todas.size() - 1);
    }

    static UUID idDe(Map<String, Object> linha) {
        return (UUID) linha.get("id");
    }

    Set<String> artefatosGuardadosDe(UUID orderId) {
        Set<String> artefatos = new LinkedHashSet<>();
        for (Map<String, Object> l : tentativasDe(orderId)) {
            if (l.get("pix_copy_paste") != null) artefatos.add((String) l.get("pix_copy_paste"));
        }
        return artefatos;
    }

    BigDecimal totalDoPedido(UUID orderId) {
        return jdbc.queryForObject("SELECT subtotal + shipping_cost FROM orders WHERE id = ?",
                BigDecimal.class, orderId);
    }

    String statusDoPedido(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    List<UUID> pedidosDe(Cliente cliente) {
        return jdbc.queryForList("SELECT id FROM orders WHERE user_id = ? ORDER BY created_at, id",
                UUID.class, cliente.userId());
    }

    UUID oUnicoPedidoDe(Cliente cliente) {
        List<UUID> pedidos = pedidosDe(cliente);
        assertThat(pedidos)
                .as("guarda: o cliente tem exatamente um pedido — zero é intenção perdida, dois é "
                        + "retomada que duplicou")
                .hasSize(1);
        return pedidos.get(0);
    }

    

    void envelhecer(UUID paymentId, Duration quanto) {
        assertThat(jdbc.update("UPDATE payments SET created_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minus(quanto)), paymentId))
                .as("guarda: envelhecer tocou exatamente a tentativa %s", paymentId)
                .isEqualTo(1);
    }

    void vencerPrazoDoPedido(UUID orderId) {
        assertThat(jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofMinutes(1))), orderId))
                .as("guarda: o prazo plantado tocou o pedido %s", orderId)
                .isEqualTo(1);
    }

    


    int sessoesEsperandoLockEmPayments(String verbo) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                 WHERE datname = current_database()
                   AND pid <> pg_backend_pid()
                   AND wait_event_type = 'Lock'
                   AND query ILIKE ?
                """, Integer.class, "%" + verbo + "%payments%");
        return n == null ? 0 : n;
    }

    

    Connection transacaoAberta() throws SQLException {
        Connection c = dataSource.getConnection();
        c.setAutoCommit(false);
        return c;
    }

    

    Connection travarTentativasDe(UUID orderId) throws SQLException {
        Connection c = transacaoAberta();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM payments WHERE order_id = ? FOR UPDATE")) {
            ps.setObject(1, orderId);
            ps.executeQuery().close();
        }
        return c;
    }

    Connection travarTentativa(UUID paymentId) throws SQLException {
        Connection c = transacaoAberta();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM payments WHERE id = ? FOR UPDATE")) {
            ps.setObject(1, paymentId);
            ps.executeQuery().close();
        }
        return c;
    }

    static void fechar(Connection c, boolean commit) throws SQLException {
        try {
            if (commit) c.commit(); else c.rollback();
        } finally {
            c.close();
        }
    }

    

    record Cliente(String token, UUID addressId, UUID userId) {}

    record Admin(String token, UUID id) {}

    

    record Resposta(int status, String corpo) {
        String code() { return ler(corpo, "$.code"); }
        String mensagem() { return ler(corpo, "$.message"); }
        String id() { return ler(corpo, "$.id"); }
        String pedidoStatus() { return ler(corpo, "$.status"); }
        String pixCopyPaste() { return ler(corpo, "$.pix.copyPaste"); }
        String pixTxid() { return ler(corpo, "$.pix.txid"); }
        boolean temPix() { return ler(corpo, "$.pix") != null; }
        boolean doisXX() { return status >= 200 && status < 300; }

        @Override
        public String toString() {
            return "HTTP " + status + " " + corpo;
        }
    }

    Resposta executar(RequestBuilder requisicao) throws Exception {
        MvcResult r = mockMvc.perform(requisicao).andReturn();
        return new Resposta(r.getResponse().getStatus(),
                r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    RequestBuilder checkout(Cliente cliente) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + cliente.addressId() + "\"}");
    }

    Resposta postarCheckout(Cliente cliente) throws Exception {
        return executar(checkout(cliente));
    }

    Resposta lerPedido(Cliente cliente, UUID orderId) throws Exception {
        return executar(get("/me/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()));
    }

    static final String ROTA_DA_FILA = "/admin/payments/pending-review";
    static final String ROTA_DA_DECISAO = "/admin/payments/{id}/review";

    Resposta lerFila(String token, String query) throws Exception {
        var req = get(ROTA_DA_FILA + (query == null ? "" : query));
        if (token != null) req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return executar(req);
    }

    Resposta decidir(String token, UUID paymentId, String outcome) throws Exception {
        var req = post(ROTA_DA_DECISAO, paymentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + outcome + "\"}");
        if (token != null) req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return executar(req);
    }

    


    void decidirComoPassoDoRoteiro(Admin admin, UUID paymentId) throws Exception {
        Resposta r = decidir(admin.token(), paymentId, "NOT_CREATED");
        assertThat(r.doisXX())
                .as("guarda do roteiro: a decisão humana K5 (POST %s, ADMIN, outcome NOT_CREATED) "
                        + "sobre uma cobrança PENDING_CONFIRMATION há mais de X tem de vencer. "
                        + "Veio: %s. Hoje a rota não existe — vermelho de produto da DV0-c "
                        + "(ADR 0016 §3.4)", ROTA_DA_DECISAO, r)
                .isTrue();
    }

    static <T> T ler(String corpo, String caminho) {
        if (corpo == null || corpo.isBlank()) return null;
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

    

    List<String> artefatosEm2xxForaDePayments(UUID orderId, Collection<Resposta> respostas) {
        Set<String> guardados = artefatosGuardadosDe(orderId);
        List<String> violacoes = new ArrayList<>();
        for (Resposta r : respostas) {
            if (r.doisXX() && r.pixCopyPaste() != null && !guardados.contains(r.pixCopyPaste())) {
                violacoes.add(r.pixCopyPaste() + " (de " + r + ")");
            }
        }
        return violacoes;
    }

    

    Cliente novoClienteComEndereco() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "dv0c-" + m + "@teste.local";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"%s","password":"senha-forte-123",
                                 "phone":"%s"}""".formatted(m, email, TELEFONE_DO_CLIENTE)))
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
                                 "complement":null,"district":"Centro","city":"São Paulo",
                                 "uf":"SP"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        return new Cliente(token, UUID.fromString(ler(endereco, "$.id")), userId);
    }

    
    Cliente clienteComCarrinho(String preco) throws Exception {
        Cliente c = novoClienteComEndereco();
        adicionarAoCarrinho(c, criarPeca(preco));
        return c;
    }

    Admin novoAdmin() {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User admin = userRepository.save(User.createWithRole(
                "Operador " + m, "admin-" + m + "@teste.local", "hash", Role.ADMIN));
        return new Admin(jwtService.generate(admin), admin.getId());
    }

    String tokenDe(Role papel) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User u = userRepository.save(User.createWithRole(
                "Papel " + m, papel.name().toLowerCase() + "-" + m + "@teste.local", "hash", papel));
        return jwtService.generate(u);
    }

    UUID criarPeca(String preco) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        Product produto = productRepository.save(Product.create(dona, categoria, "Peça " + m,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal(preco)));
        return produto.getId();
    }

    void adicionarAoCarrinho(Cliente cliente, UUID productId) throws Exception {
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isOk());
    }

    

    UUID pedidoDepoisDeFalha(Cliente cliente, PortaDeCobrancaControlavel.Modo modo) throws Exception {
        porta.modo(modo);
        Resposta r = postarCheckout(cliente);
        assertThat(r.status())
                .as("guarda do roteiro: a primeira tentativa (porta em %s) responde 502", modo)
                .isEqualTo(502);
        porta.modo(PortaDeCobrancaControlavel.Modo.NORMAL);
        return oUnicoPedidoDe(cliente);
    }
}
