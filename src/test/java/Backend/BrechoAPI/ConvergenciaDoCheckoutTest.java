package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.exception.PaymentGatewayException;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.service.OrderExpiryService;
import Backend.BrechoAPI.service.PaymentGateway;
import Backend.BrechoAPI.service.ShippingService;
import Backend.BrechoAPI.service.impl.FakePixPaymentGateway;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;











































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)





@TestPropertySource(properties = "checkout.order-ttl=PT30M")
class ConvergenciaDoCheckoutTest {

    








    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    

    




    @TestConfiguration
    static class GatewayDesteTeste {

        @Bean
        @Primary
        Gateway gatewayControlavel() {
            return new Gateway();
        }
    }

    




























    static class Gateway implements PaymentGateway {

        enum Modo { NORMAL, FALHA_TRADUZIDA, FALHA_CRUA }

        


        static final String SEGREDO_DA_CAUSA =
                "connect timed out: gateway.interno.local:8443 token=abc-123-segredo";

        
        record Chamada(UUID orderId, boolean dentroDeTransacao, PixCharge cobranca,
                       Modo modo) {}

        private final PaymentGateway real = new FakePixPaymentGateway();
        private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();

        private volatile Modo modo = Modo.NORMAL;

        @Override
        public PixCharge createPixCharge(UUID orderId, BigDecimal amount, Instant expiresAt) {
            
            
            
            boolean dentroDeTransacao = TransactionSynchronizationManager.isActualTransactionActive();

            
            
            PixCharge cobranca = real.createPixCharge(orderId, amount, expiresAt);
            Modo modoDaChamada = modo;
            chamadas.add(new Chamada(orderId, dentroDeTransacao, cobranca, modoDaChamada));

            return switch (modoDaChamada) {
                case NORMAL -> cobranca;
                case FALHA_TRADUZIDA -> throw new PaymentGatewayException(
                        "não foi possível gerar a cobrança Pix agora; seu pedido está registrado"
                                + " e você pode tentar de novo em instantes",
                        new IOException(SEGREDO_DA_CAUSA));
                case FALHA_CRUA -> throw new IllegalStateException(SEGREDO_DA_CAUSA);
            };
        }

        void modo(Modo novo) {
            this.modo = novo;
        }

        void esquecerChamadas() {
            chamadas.clear();
        }

        List<Chamada> chamadas() {
            return List.copyOf(chamadas);
        }

        Chamada ultimaChamada() {
            List<Chamada> feitas = chamadas();
            assertThat(feitas)
                    .as("o gateway NÃO foi chamado. Um 502 (ou um 201) que chega sem passar pela "
                            + "fronteira externa não prova nada sobre convergência: pode ser bean "
                            + "não substituído, rota que nem chegou ao checkout, ou handler "
                            + "respondendo por outra causa")
                    .isNotEmpty();
            return feitas.get(feitas.size() - 1);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired Gateway gateway;
    @Autowired PaymentGateway portaInjetadaNoSistema;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired OrderExpiryService orderExpiryService;
    @Autowired ShippingService shippingService;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    @BeforeEach
    void gatewayNoAr() {
        gateway.modo(Gateway.Modo.NORMAL);
        gateway.esquecerChamadas();
    }

    

    



















    @Test
    void oGatewayDesteTesteEstaMesmoNoAr() {
        assertThat(portaInjetadaNoSistema)
                .as("o bean de PaymentGateway que o CheckoutService recebe tem que ser o deste "
                        + "teste; se vier o FakePixPaymentGateway de produção, a substituição "
                        + "@Primary não aconteceu e nenhuma falha de gateway é alcançável aqui")
                .isSameAs(gateway);

        UUID pedido = UUID.fromString("c0117e12-0000-4000-8000-000000000001");
        BigDecimal valor = new BigDecimal("199.90");
        Instant vencimento = clock.instant().plus(Duration.ofMinutes(30));

        gateway.modo(Gateway.Modo.FALHA_TRADUZIDA);
        assertThat(catchThrowable(() -> gateway.createPixCharge(pedido, valor, vencimento)))
                .as("o botão de falha precisa mesmo falhar — e com a exceção da fronteira, que é "
                        + "a que o GlobalExceptionHandler traduz em 502")
                .isInstanceOf(PaymentGatewayException.class);

        gateway.modo(Gateway.Modo.NORMAL);
        assertThat(gateway.createPixCharge(pedido, valor, vencimento))
                .as("no ar, este bean devolve A COBRANÇA DO ADAPTADOR DE PRODUÇÃO. Se ele "
                        + "fabricasse a própria, a idempotência provada na retomada seria a dele")
                .isEqualTo(new FakePixPaymentGateway().createPixCharge(pedido, valor, vencimento));
    }

    




























    @Test
    void oObservadorDeTransacaoEnxergaUmaTransacaoQuandoElaExiste() {
        UUID pedido = UUID.fromString("c0117e12-0000-4000-8000-000000000002");

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                gateway.createPixCharge(pedido, new BigDecimal("10.00"),
                        clock.instant().plus(Duration.ofMinutes(30))));

        assertThat(gateway.ultimaChamada().dentroDeTransacao())
                .as("o observador precisa saber dizer TRUE, senão o FALSE do teste 7 é vácuo: "
                        + "isActualTransactionActive() devolve false por AUSÊNCIA, e ausência de "
                        + "transação é indistinguível de ausência de instrumento")
                .isTrue();
    }

    

    



























    @Test
    void gatewayForaDoArDevolve502EOPedidoFica() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));
        adicionarAoCarrinho(cliente.token(), criarPeca("130.00"));

        gateway.modo(Gateway.Modo.FALHA_TRADUZIDA);

        MvcResult falha = mockMvc.perform(checkout(cliente))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"))
                .andReturn();

        
        
        
        assertThat(gateway.ultimaChamada().modo())
                .as("o 502 tem que vir da fronteira externa que este teste derrubou")
                .isEqualTo(Gateway.Modo.FALHA_TRADUZIDA);

        assertThat(falha.getResponse().getContentAsString())
                .as("a mensagem do fornecedor carrega endereço interno e credencial; o handler "
                        + "guarda a causa e não a serializa")
                .doesNotContain(Gateway.SEGREDO_DA_CAUSA);

        
        List<UUID> pedidos = pedidosDe(cliente);
        assertThat(pedidos)
                .as("A TESE INTEIRA ESTÁ AQUI: a transação 1 commitou ANTES do efeito externo, "
                        + "então a falha do gateway não pode apagar o pedido. Zero linha aqui "
                        + "significa um desenho que chama o gateway antes de gravar — e aí um "
                        + "timeout depois da criação da cobrança deixa cobrança viva sem pedido")
                .hasSize(1);
        UUID orderId = pedidos.get(0);

        assertThat(statusDoPedido(orderId))
                .as("o pedido fica ABERTO, esperando a retomada — não nasce morto")
                .isEqualTo("AWAITING_PAYMENT");

        
        assertThat(subtotalDoPedido(orderId))
                .as("70.00 + 130.00: um subtotal que fosse 'o preço do primeiro item' daria "
                        + "70.00 e passaria em qualquer asserção de existência")
                .isEqualByComparingTo("200.00");
        assertThat(freteDoPedido(orderId))
                .as("o frete congelado é o da UF do endereço escolhido")
                .isEqualByComparingTo(shippingService.costFor(UF));
        assertThat(precosCongeladosDe(orderId))
                .as("os itens do pedido também sobreviveram — um pedido sem itens seria uma "
                        + "casca, e a intenção registrada não diria o que a cliente quis comprar")
                .containsExactlyInAnyOrder(new BigDecimal("70.00"), new BigDecimal("130.00"));

        
        assertThat(cobrancaGravadaDe(orderId))
                .as("uma linha em payments, e ela diz 'quis cobrar, não sei se a cobrança "
                        + "existe'. Nenhuma linha significaria que a intenção não é durável; "
                        + "txid preenchido significaria que gravamos uma cobrança que o gateway "
                        + "nunca confirmou")
                .isEqualTo(new Cobranca(null, null));

        
        
        
        
        
        assertThat(statusDaUltimaTentativaDe(orderId))
                .as("G2 / K3 — falha certa: a tentativa de cobrança vai a NOT_CREATED, que "
                        + "libera a retomada (ADR 0016 §2)")
                .isEqualTo("NOT_CREATED");

        
        mockMvc.perform(get("/me/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").value(200.00))
                .andExpect(jsonPath("$.pix").doesNotExist());
    }

    

























    @Test
    void falhaCruaDoGatewayTambemVira502EOPedidoFica() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        gateway.modo(Gateway.Modo.FALHA_CRUA);

        MvcResult falha = mockMvc.perform(checkout(cliente))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("PAYMENT_GATEWAY_UNAVAILABLE"))
                .andReturn();

        assertThat(gateway.ultimaChamada().modo()).isEqualTo(Gateway.Modo.FALHA_CRUA);

        assertThat(falha.getResponse().getContentAsString())
                .as("exceção de terceiro não traduzida é justamente a que mais vaza: o "
                        + "orquestrador escreve a mensagem do cliente e guarda a original na causa")
                .doesNotContain(Gateway.SEGREDO_DA_CAUSA);

        List<UUID> pedidos = pedidosDe(cliente);
        assertThat(pedidos)
                .as("uma falha não traduzida é falha do MESMO jeito: o commit da transação 1 já "
                        + "aconteceu e nada aqui o desfaz")
                .hasSize(1);
        assertThat(statusDoPedido(pedidos.get(0))).isEqualTo("AWAITING_PAYMENT");
        assertThat(cobrancaGravadaDe(pedidos.get(0)))
                .as("mesma forma de intenção do teste 1 — o caminho da exceção não muda o que "
                        + "ficou no banco")
                .isEqualTo(new Cobranca(null, null));

        
        
        
        
        
        
        
        
        UUID orderId = pedidos.get(0);
        assertThat(statusDaUltimaTentativaDe(orderId))
                .as("G1 / K4 — desconhecido: a tentativa FICA PENDING_CONFIRMATION (ADR 0016 §2)")
                .isEqualTo("PENDING_CONFIRMATION");

        gateway.modo(Gateway.Modo.NORMAL);
        gateway.esquecerChamadas();
        mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.pix").doesNotExist());
        assertThat(gateway.chamadas())
                .as("G1 — o POST seguinte NÃO chama a porta: retomar o desconhecido é o retry "
                        + "automático que a 0015 D-3 proíbe")
                .isEmpty();
    }

    

    










































    @Test
    void retomarDepoisQueOGatewayVoltaNaoCriaSegundoPedido() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        
        gateway.modo(Gateway.Modo.FALHA_TRADUZIDA);
        mockMvc.perform(checkout(cliente)).andExpect(status().isBadGateway());

        PaymentGateway.PixCharge cobrancaPerdida = gateway.ultimaChamada().cobranca();
        UUID orderId = oUnicoPedidoDe(cliente);

        
        gateway.modo(Gateway.Modo.NORMAL);

        MvcResult retomada = mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty())
                .andExpect(jsonPath("$.pix.txid").isNotEmpty())
                .andReturn();

        String corpo = retomada.getResponse().getContentAsString();

        assertThat(pedidosDe(cliente))
                .as("nada duplica: retomada é o MESMO pedido. 200 com linha nova é pedido "
                        + "duplicado com cara de idempotência — e duas cobranças Pix vivas")
                .containsExactly(orderId);
        
        
        
        
        
        
        
        
        
        assertThat(quantasCobrancasVivasDe(orderId))
                .as("e uma cobrança VIVA só: uk_payments_order (índice parcial, ADR 0016 §3.1) é "
                        + "a rede de baixo; linhas NOT_CREATED podem existir")
                .isEqualTo(1);

        
        Cobranca gravada = cobrancaGravadaDe(orderId);
        assertThat(gravada)
                .as("o código entregue na retomada tem que ser o guardado — senão ele existe por "
                        + "uma resposta HTTP só e some quando a tela fecha")
                .isEqualTo(new Cobranca(ler(corpo, "$.pix.txid"), ler(corpo, "$.pix.copyPaste")));

        
        assertThat(gravada.txid())
                .as("A COBRANÇA QUE A RETOMADA ANEXA É A QUE JÁ EXISTIA DO LADO DO FORNECEDOR. "
                        + "Um txid diferente aqui significa segunda cobrança criada: a primeira "
                        + "fica viva no gateway, pagável, sem pedido nenhum esperando por ela — "
                        + "que é o estado externo órfão que esta fase inteira existe para não criar")
                .isEqualTo(cobrancaPerdida.txid());
        assertThat(gravada.copyPaste()).isEqualTo(cobrancaPerdida.copyPaste());
    }

    























    @Test
    void retomadaDePedidoQueJaTemCobrancaNaoChamaOGatewayDeNovo() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        MvcResult primeiro = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(primeiro);

        assertThat(gateway.chamadas())
                .as("guarda: o pedido novo chamou o gateway UMA vez — sem isto, a asserção "
                        + "abaixo compararia dois zeros")
                .hasSize(1);
        Cobranca cobrancaDoPrimeiro = cobrancaGravadaDe(orderId);

        
        mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.pix.txid").value(cobrancaDoPrimeiro.txid()));

        assertThat(gateway.chamadas())
                .as("A RETOMADA NÃO PODE TOCAR O GATEWAY quando a cobrança já está anexada. "
                        + "Nenhuma asserção de VALOR pega isto: o txid é o mesmo pelos dois "
                        + "caminhos, porque a porta é idempotente. Só o caminho distingue — e é "
                        + "por ele que uma segunda cobrança viva entraria no dia em que o "
                        + "adaptador real trouxer uma borda de idempotência diferente da nossa")
                .hasSize(1);
    }

    

    

    private static final String SENTINELA_DV0A =
            "SENTINELA-DV0-A::retomada-pelo-POST::nao-e-BR-Code";

    



































    @Test
    void retomadaDevolveOArtefatoGuardadoENaoUmRegerado() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        MvcResult primeiro = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(primeiro);
        assertThat(gateway.chamadas())
                .as("guarda: o pedido novo chamou a porta UMA vez — sem isto, o zero abaixo "
                        + "compararia dois zeros")
                .hasSize(1);

        Cobranca original = cobrancaGravadaDe(orderId);
        assertThat(original.copyPaste())
                .as("guarda: a cobrança original existe e NÃO é o sentinela — senão a troca "
                        + "abaixo não muda nada e o teste passa por coincidência")
                .isNotNull()
                .isNotEqualTo(SENTINELA_DV0A);

        
        assertThat(jdbc.update("UPDATE payments SET pix_copy_paste = ? WHERE order_id = ?",
                SENTINELA_DV0A, orderId))
                .as("guarda: a escrita do sentinela tocou exatamente a linha deste pedido")
                .isEqualTo(1);

        gateway.esquecerChamadas();

        MvcResult volta = mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andReturn();

        assertThat(gateway.chamadas())
                .as("DV0-a, caso 1 — A VOLTA AO CHECKOUT NÃO PODE TOCAR A PORTA quando há "
                        + "artefato guardado (item 10 do Pedro: 'nunca chama o gateway de novo')")
                .isEmpty();

        String corpo = volta.getResponse().getContentAsString();
        assertThat((String) ler(corpo, "$.pix.copyPaste"))
                .as("sentinela esperado × encontrado: a volta tem de devolver O ARTEFATO GUARDADO "
                        + "na coluna. Um BR Code aqui é a resposta REGERANDO o artefato em vez de "
                        + "lê-lo — verde hoje com o adaptador falso, e o link errado no dia em "
                        + "que o fornecedor real não for função pura do orderId")
                .isEqualTo(SENTINELA_DV0A);
        assertThat((String) ler(corpo, "$.pix.txid"))
                .as("a referência devolvida é a guardada, intocada")
                .isEqualTo(original.txid());

        assertThat(cobrancaGravadaDe(orderId))
                .as("leitura pós-escrita: a volta não reescreveu a linha de payments")
                .isEqualTo(new Cobranca(original.txid(), SENTINELA_DV0A));
    }

    

    

























    @Test
    void pedidoQueNinguemRetomaNaoTravaOClienteParaSempre() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        gateway.modo(Gateway.Modo.FALHA_TRADUZIDA);
        Instant t0 = clock.instant();
        mockMvc.perform(checkout(cliente)).andExpect(status().isBadGateway());

        UUID abandonado = oUnicoPedidoDe(cliente);
        assertThat(cobrancaGravadaDe(abandonado))
                .as("guarda: o pedido abandonado é o da falha — sem cobrança nenhuma")
                .isEqualTo(new Cobranca(null, null));

        
        assertThat(orderExpiryService.expireDueOf(cliente.userId(), t0.plus(DEPOIS_DO_PRAZO)))
                .as("passado o prazo, o pedido que o gateway deixou pela metade é fechado")
                .isEqualTo(1);
        assertThat(statusDoPedido(abandonado))
                .as("EXPIRED é o que o índice parcial deixa de enxergar — é a escrita que "
                        + "destrava, e ela tem que estar na COLUNA status, não numa projeção")
                .isEqualTo("EXPIRED");

        
        gateway.modo(Gateway.Modo.NORMAL);
        MvcResult novo = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty())
                .andReturn();

        assertThat(idDoPedido(novo))
                .as("201 e id NOVO: uma indisponibilidade do gateway não pode custar à cliente "
                        + "a capacidade de comprar. 200 com o id velho significaria que ela "
                        + "recebeu de volta o pedido que o gateway abandonou")
                .isNotEqualTo(abandonado);
        assertThat(pedidosDe(cliente))
                .as("dois pedidos: o abandonado (EXPIRED) e o novo (AWAITING_PAYMENT)")
                .hasSize(2);
    }

    

    


























    @Test
    void aChamadaAoGatewayAconteceForaDaTransacao() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00"));

        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        assertThat(gateway.chamadas())
                .as("guarda: a chamada tem que ter acontecido, senão não há o que observar")
                .hasSize(1);
        assertThat(gateway.ultimaChamada().dentroDeTransacao())
                .as("a chamada externa roda FORA de transação. TRUE aqui significa que alguém "
                        + "pôs @Transactional no orquestrador (ou juntou orquestrador e bean "
                        + "transacional na mesma classe): a partir daí, um gateway lento segura "
                        + "conexão do pool e lock de banco, e o timeout de um terceiro vira "
                        + "indisponibilidade do brechó inteiro")
                .isFalse();
    }

    




























































































































































































































































































    







































    















































    

    private static final String TELEFONE_DO_CLIENTE = "11987654321";

    



    private static final UfBrasileira UF = UfBrasileira.SP;

    


    private static final Duration DEPOIS_DO_PRAZO = Duration.ofMinutes(31);

    private record Cliente(String token, UUID addressId, UUID userId) {}

    



    private record Cobranca(String txid, String copyPaste) {}

    private RequestBuilder checkout(Cliente cliente) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + cliente.addressId() + "\"}");
    }

    private UUID idDoPedido(MvcResult resultado) throws Exception {
        return UUID.fromString(ler(resultado.getResponse().getContentAsString(), "$.id"));
    }

    



    private List<UUID> pedidosDe(Cliente cliente) {
        return jdbc.queryForList(
                "SELECT id FROM orders WHERE user_id = ? ORDER BY created_at, id",
                UUID.class, cliente.userId());
    }

    









    private UUID oUnicoPedidoDe(Cliente cliente) {
        List<UUID> pedidos = pedidosDe(cliente);
        assertThat(pedidos)
                .as("o pedido deste cliente tem que existir e ser um só: lista vazia significa "
                        + "que a intenção NÃO sobreviveu (a falha do gateway levou o pedido "
                        + "junto), e mais de um significa que a retomada duplicou")
                .hasSize(1);
        return pedidos.get(0);
    }

    private String statusDoPedido(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    private BigDecimal subtotalDoPedido(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT subtotal FROM orders WHERE id = ?", BigDecimal.class, orderId);
    }

    private BigDecimal freteDoPedido(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT shipping_cost FROM orders WHERE id = ?", BigDecimal.class, orderId);
    }

    private List<BigDecimal> precosCongeladosDe(UUID orderId) {
        return jdbc.queryForList("SELECT unit_price FROM order_items WHERE order_id = ?",
                BigDecimal.class, orderId);
    }

    





    private int quantasCobrancasVivasDe(UUID orderId) {
        return (int) jdbc.queryForList("SELECT * FROM payments WHERE order_id = ?", orderId)
                .stream()
                .filter(linha -> !"NOT_CREATED".equals(linha.get("status")))
                .count();
    }

    

    private Object statusDaUltimaTentativaDe(UUID orderId) {
        List<java.util.Map<String, Object>> tentativas = jdbc.queryForList(
                "SELECT * FROM payments WHERE order_id = ? ORDER BY created_at DESC, id DESC",
                orderId);
        assertThat(tentativas).as("guarda: o pedido tem ao menos uma tentativa").isNotEmpty();
        return tentativas.get(0).get("status");
    }

    









    private Cobranca cobrancaGravadaDe(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT txid, pix_copy_paste FROM payments WHERE order_id = ?"
                        + " ORDER BY created_at DESC, id DESC LIMIT 1",
                (rs, linha) -> new Cobranca(rs.getString("txid"), rs.getString("pix_copy_paste")),
                orderId);
    }

    






    private static <T> T ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

    

    private Cliente novoClienteComEndereco() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "convergencia-" + m + "@teste.local";

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
