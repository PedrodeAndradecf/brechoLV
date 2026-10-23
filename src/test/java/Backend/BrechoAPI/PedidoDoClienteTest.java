package Backend.BrechoAPI;

import Backend.BrechoAPI.config.ShippingProperties;
import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.OrderItemRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.CheckoutTransactionService;
import Backend.BrechoAPI.service.ShippingService;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;












































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PedidoDoClienteTest {

    






































































































































































































































































































































































































































































































































    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    











    @TestConfiguration
    static class TabelaDeFreteDesteTeste {

        @Bean
        @Primary
        ShippingProperties tabelaDeFreteDoTeste() {
            return new ShippingProperties(Map.of(
                    "SP", new BigDecimal("19.90"),
                    "MG", new BigDecimal("20")));   
        }
    }

    
    private static final BigDecimal FRETE_SP = new BigDecimal("19.90");

    


    private static final BigDecimal FRETE_REAJUSTADO = new BigDecimal("99.90");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    










    @MockitoSpyBean ShippingService shippingService;

    

    















    @Test
    void aTabelaDeFreteDesteTesteEstaMesmoNoAr() {
        assertThat(shippingService.costFor(UfBrasileira.SP))
                .as("o bean @Primary deste teste diz 19.90 para SP; outro valor significa que a "
                        + "substituição não aconteceu e todo total deste arquivo está sendo "
                        + "comparado contra a tabela errada")
                .isEqualByComparingTo(FRETE_SP);

        assertThat(shippingService.costFor(UfBrasileira.MG))
                .as("MG é o eixo do achado K e vale 20 nesta tabela")
                .isEqualByComparingTo(new BigDecimal("20"));

        assertThat(catchThrowable(() -> shippingService.costFor(UfBrasileira.AC)))
                .as("AC está fora da tabela deste teste: se não recusar, o spy não está "
                        + "delegando ao ShippingService real")
                .isInstanceOf(DomainRuleException.class);
    }

    

    





















    @Test
    void clienteLeOProprioPedidoComOMesmoCodigoPix() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca peca = criarPeca("Bolsa", "60.00");
        adicionarAoCarrinho(cliente.token(), peca.productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty())
                .andReturn();
        UUID orderId = idDoPedido(criacao);
        String copyPasteDoPost = ler(corpoDe(criacao), "$.pix.copyPaste");
        String txidDoPost = ler(corpoDe(criacao), "$.pix.txid");

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(UUID.fromString(ler(corpo, "$.id")))
                .as("a rota devolve O pedido pedido, não outro qualquer")
                .isEqualTo(orderId);
        assertThat(ler(corpo, "$.pix.copyPaste").toString())
                .as("o código Pix da releitura tem que ser BYTE A BYTE o do POST: é a prova nº 1 "
                        + "da fase, e ela só vale se a cliente puder fechar a tela e voltar")
                .isEqualTo(copyPasteDoPost);
        assertThat(ler(corpo, "$.pix.txid").toString())
                .isEqualTo(txidDoPost);

        assertThat(ler(corpo, "$.pix.copyPaste").toString())
                .as("e ele tem que ser o que ficou DURÁVEL em payments.pix_copy_paste — um "
                        + "código gerado de novo na leitura seria plausível e estaria errado")
                .isEqualTo(copyPasteGravado(orderId));

        
        assertThat(ler(corpo, "$.status").toString()).isEqualTo("AWAITING_PAYMENT");
        assertThat(corpo).contains("\"subtotal\":60.00");
        assertThat(corpo).contains("\"shippingCost\":19.90");
        assertThat(corpo).contains("\"total\":79.90");
    }

    

    















    @Test
    void clienteNaoLeOPedidoDeOutro() throws Exception {
        Cliente dona = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(dona.token(), criarPeca("Saia", "70.00").productId());
        UUID pedidoDaDona = idDoPedido(mockMvc.perform(checkout(dona))
                .andExpect(status().isCreated())
                .andReturn());

        Cliente intrusa = novoClienteComEndereco(UfBrasileira.SP);

        String corpo = corpoDe(mockMvc.perform(lerPedido(intrusa, pedidoDaDona))
                .andExpect(status().isNotFound())
                .andReturn());

        assertThat(codigoDe(corpo))
                .as("404 com $.code = ORDER_NOT_FOUND. Nulo aqui significa que a resposta nem "
                        + "chegou a um @ExceptionHandler — o caso da ROTA QUE NÃO EXISTE, que "
                        + "também responde 404 e faria este teste passar por nada (run S1). "
                        + "ACCESS_DENIED ou 403 significa que a posse virou checagem em vez de "
                        + "cláusula WHERE, e a resposta passou a revelar que o pedido existe. "
                        + "Corpo: %s", corpo)
                .isEqualTo("ORDER_NOT_FOUND");

        assertThat(corpo)
                .as("e a resposta não pode vazar nada do pedido alheio — nem o id que a intrusa "
                        + "já tinha, junto de qualquer dado dele")
                .doesNotContain("subtotal")
                .doesNotContain("pix");
    }

    































    @Test
    void pedidoInexistenteSaiPelaMesmaPorta() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andReturn());

        assertThat(codigoDe(corpo))
                .as("id inexistente e id de outra dona são a MESMA resposta: rota que os "
                        + "distinguisse viraria oráculo de existência. Corpo: %s", corpo)
                .isEqualTo("ORDER_NOT_FOUND");
    }

    

    

































    @Test
    void pedidoVencidoNoRelogioApareceComoExpirado() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("Casaco", "80.00").productId());
        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        
        
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofHours(1))), orderId);
        assertThat(statusGravado(orderId))
                .as("guarda: o pedido tem que chegar ao GET AINDA aberto no banco, senão a "
                        + "projeção não é exercitada e este teste vira uma leitura de coluna")
                .isEqualTo("AWAITING_PAYMENT");
        Timestamp tocadoAntes = updatedAtGravado(orderId);

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(ler(corpo, "$.status").toString())
                .as("o status devolvido é o EFETIVO: sem job (decisão 10), um pedido vencido "
                        + "continua AWAITING_PAYMENT guardado, e quem lê projeta EXPIRED. "
                        + "AWAITING_PAYMENT aqui significa que a rota devolveu o valor CRU")
                .isEqualTo("EXPIRED");

        assertThat(statusGravado(orderId))
                .as("e a projeção é de LEITURA: a coluna continua com o valor guardado. Um GET "
                        + "que escrevesse EXPIRED estaria mudando estado numa rota de leitura — "
                        + "e o índice parcial da decisão 8, que usa o guardado, passaria a "
                        + "depender de alguém ter aberto uma tela")
                .isEqualTo("AWAITING_PAYMENT");
        assertThat(updatedAtGravado(orderId))
                .as("nem a linha foi tocada — um UPDATE que reescrevesse o mesmo status "
                        + "continuaria sendo escrita indevida durante um GET")
                .isEqualTo(tocadoAntes);
    }

    



















    @Test
    void pedidoDentroDoPrazoApareceComoAwaitingPayment() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("Casaco", "80.00").productId());
        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(ler(corpo, "$.status").toString())
                .as("o pedido que a cliente ainda pode pagar não pode aparecer como expirado: "
                        + "ela desistiria de pagar uma cobrança que está VIVA no gateway")
                .isEqualTo("AWAITING_PAYMENT");
        assertThat(statusGravado(orderId)).isEqualTo("AWAITING_PAYMENT");
    }

    










































































    @Test
    void pedidoGuardadoComoExpiradoVoltaExpiradoNoGet() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("Casaco", "80.00").productId());
        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty())
                .andReturn();
        UUID orderId = idDoPedido(criacao);
        String copyPasteDoPost = ler(corpoDe(criacao), "$.pix.copyPaste");

        
        
        
        
        
        jdbc.update("UPDATE orders SET expires_at = ? WHERE id = ?",
                Timestamp.from(clock.instant().minus(Duration.ofHours(1))), orderId);
        adicionarAoCarrinho(cliente.token(), criarPeca("Bota", "130.00").productId());
        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        assertThat(statusGravado(orderId))
                .as("guarda e o que separa este teste do gêmeo projetado: o status tem que "
                        + "estar EXPIRED NO BANCO quando o GET roda. AWAITING_PAYMENT aqui "
                        + "significaria que o vencido não foi fechado e este teste exercitaria a "
                        + "PROJEÇÃO (ramo do then), não o ramo order.getStatus().name()")
                .isEqualTo("EXPIRED");

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(ler(corpo, "$.status").toString())
                .as("o GET devolve o status GUARDADO (EXPIRED) pelo ramo order.getStatus()."
                        + "name() de statusEfetivoDe: isAwaitingPayment() é falso, a projeção não "
                        + "entra. Era o único ramo do status efetivo que nenhum teste desta rota "
                        + "exercitava — a lacuna DV6-a")
                .isEqualTo("EXPIRED");

        
        
        
        
        
        assertThat(ler(corpo, "$.pix.copyPaste").toString())
                .as("o pix continua presente e é o do POST byte a byte mesmo com o pedido "
                        + "EXPIRED; o SIGNIFICADO disso (ainda pagável?) é o contrato de DV6-b")
                .isEqualTo(copyPasteDoPost);
    }

    

    



























    @Test
    void aOrdemDosItensEhAMesmaNoPostENoGet() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        for (int i = 1; i <= 6; i++) {
            adicionarAoCarrinho(cliente.token(), criarPeca("Peça " + i, "10.00").productId());
        }

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andReturn();
        UUID orderId = idDoPedido(criacao);
        List<String> noPost = idsDosItens(corpoDe(criacao));

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(6))
                .andReturn());
        List<String> noGet = idsDosItens(corpo);

        assertThat(noGet)
                .as("os dois montadores da resposta do pedido — o do checkout e o da leitura — "
                        + "têm que devolver a MESMA ordem, elemento a elemento. Ordens "
                        + "diferentes fazem a lista dançar na tela sem causa visível, e a "
                        + "cliente não tem como saber por quê")
                .containsExactlyElementsOf(noPost);

        assertThat(noGet)
                .as("a asserção DETERMINÍSTICA (achado B): a ordem contratada é a de "
                        + "order_items.id, ascendente — findAllByOrderIdOrderByIdAsc. Se este "
                        + "vermelho aparecer, o conserto é o ORDER BY no repositório, NÃO "
                        + "relaxar a asserção")
                .containsExactlyElementsOf(ordemGravadaPorId(orderId));
        assertThat(noPost)
                .as("e o POST obedece à mesma fonte — se ele ordenar por outro critério, os "
                        + "dois estão ordenando, mas por regras diferentes, que é o achado D "
                        + "voltando com outra roupa")
                .containsExactlyElementsOf(ordemGravadaPorId(orderId));
    }

    



























    @Test
    void aOrdenacaoDosItensMoraNoRepositorioENaoNoServico() {
        List<String> metodosDoRepositorio = Arrays
                .stream(OrderItemRepository.class.getDeclaredMethods())
                .map(Method::getName)
                .toList();

        assertThat(metodosDoRepositorio)
                .as("OrderItemRepository precisa declarar findAllByOrderIdOrderByIdAsc(UUID) "
                        + "(achado D): a ordem é parte da CONSULTA, e ordena por id — created_at "
                        + "é Instant (nanos) em memória e timestamptz (micros) no Postgres, e "
                        + "dois itens podem empatar de um lado e não do outro")
                .contains("findAllByOrderIdOrderByIdAsc");

        assertThat(metodosDoRepositorio)
                .as("e findAllByOrderId (sem ordenação) tem que SAIR: enquanto ele existir, o "
                        + "próximo leitor pode chamá-lo sem perceber o que perdeu, e a ordem "
                        + "volta a depender de quem lembrou de ordenar")
                .doesNotContain("findAllByOrderId");

        List<String> comparadoresNoServico = Arrays
                .stream(CheckoutTransactionService.class.getDeclaredFields())
                .filter(campo -> Comparator.class.isAssignableFrom(campo.getType()))
                .map(Field::getName)
                .toList();

        assertThat(comparadoresNoServico)
                .as("o Comparator ORDEM_ESTAVEL sai de CheckoutTransactionService: uma fonte, "
                        + "dois leitores. Se ele ficar, existem DUAS implementações da mesma "
                        + "ordenação e nenhum teste de comportamento as acusa — é o achado F de "
                        + "T11 acontecendo de novo, com outro nome")
                .isEmpty();
    }

    

    





























    @Test
    void congelamentoValeTambemNoGet() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca cara = criarPeca("Vestido caro", "100.00");
        Peca barata = criarPeca("Lenço", "30.00");
        adicionarAoCarrinho(cliente.token(), cara.productId());
        adicionarAoCarrinho(cliente.token(), barata.productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();
        UUID orderId = idDoPedido(criacao);
        assertThat(corpoDe(criacao)).contains("\"subtotal\":130.00");
        assertThat(corpoDe(criacao)).contains("\"total\":149.90");

        
        String tituloNovo = "Vestido REBATIZADO depois da compra";
        editarPeca(cara, "500.00", tituloNovo);

        
        mockMvc.perform(get("/products/{id}", cara.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(500.00))
                .andExpect(jsonPath("$.title").value(tituloNovo));

        doReturn(FRETE_REAJUSTADO).when(shippingService).costFor(any());

        
        assertThat(shippingService.costFor(UfBrasileira.SP))
                .as("a tabela AGORA diz 99.90 para SP; se ainda disser 19.90, este teste não "
                        + "está exercitando reajuste nenhum")
                .isEqualByComparingTo(FRETE_REAJUSTADO);

        
        
        
        clearInvocations(shippingService);

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(unitPriceNaResposta(corpo, cara.productId()))
                .as("o GET tem que trazer o preço CONGELADO (100.00); 500.00 significa que a "
                        + "montagem da LEITURA foi ao Product atual — o histórico se reescreve")
                .isEqualByComparingTo("100.00");
        assertThat(unitPriceNaResposta(corpo, barata.productId()))
                .as("a peça que ninguém tocou também não pode ter mudado")
                .isEqualByComparingTo("30.00");
        assertThat(tituloNaResposta(corpo, cara.productId()))
                .as("o pedido continua dizendo o que a cliente comprou — é o que o relatório da "
                        + "Fase 5 vai ler e o que ela compara com a caixa que chegou")
                .isEqualTo(cara.titulo());

        assertThat(corpo)
                .as("o subtotal do GET é a soma CONGELADA; um recálculo sobre os preços de hoje "
                        + "daria 530.00 e o pedido deixaria de fechar consigo mesmo")
                .contains("\"subtotal\":130.00");
        assertThat(corpo)
                .as("o frete vem da COLUNA orders.shipping_cost; 99.90 é recálculo pela tabela "
                        + "de hoje")
                .contains("\"shippingCost\":19.90");
        assertThat(corpo)
                .as("e o total é a soma das duas parcelas congeladas: 130.00 + 19.90")
                .contains("\"total\":149.90");
        assertThat(corpo)
                .as("nada do mundo novo pode aparecer em lugar nenhum da resposta")
                .doesNotContain("500.00")
                .doesNotContain("530.00")
                .doesNotContain("99.90")
                .doesNotContain(tituloNovo);

        
        
        assertThat(numeroNoCorpo(corpo, "total"))
                .isEqualByComparingTo(numeroNoCorpo(corpo, "subtotal")
                        .add(numeroNoCorpo(corpo, "shippingCost")));

        verify(shippingService, never()).costFor(any());
        
        
        
        
        
    }

    

    





























    @Test
    void complementNuloVoltaNuloNoGet() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP, null);
        adicionarAoCarrinho(cliente.token(), criarPeca("Sapato", "90.00").productId());
        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(complementGravado(orderId))
                .as("guarda: o pedido tem que ter nascido SEM complemento, senão este teste "
                        + "exercita o outro caso")
                .isNull();

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping", hasKey("complement")))
                .andExpect(jsonPath("$.shipping.complement").value(nullValue()))
                .andReturn());

        assertThat(corpo)
                .as("a chave precisa estar na resposta, com valor nulo: omitir o campo é um "
                        + "contrato DIFERENTE de trazê-lo nulo, e a diferença aparece no "
                        + "consumidor. complement é a única parte opcional do snapshot")
                .contains("\"complement\":null");
    }

    












    @Test
    void complementPreenchidoVoltaNoGet() throws Exception {
        String complemento = "Apto 42, fundos";
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP, complemento);
        adicionarAoCarrinho(cliente.token(), criarPeca("Sapato", "90.00").productId());
        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(complementGravado(orderId))
                .as("guarda: o complemento foi congelado na linha do pedido — é cópia do "
                        + "endereço, não referência")
                .isEqualTo(complemento);

        mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipping.complement").value(complemento));
    }

    




























































    @Test
    void osNoveCamposDoSnapshotVoltamNoGet() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP, "Anexo 7, portão azul");
        adicionarAoCarrinho(cliente.token(), criarPeca("Bolsa", "90.00").productId());
        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        
        
        String nomeDoCliente = jdbc.queryForObject(
                "SELECT name FROM users WHERE id = ?", String.class, cliente.userId());

        mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                
                
                
                .andExpect(jsonPath("$.shipping.recipientName").value(nomeDoCliente))
                .andExpect(jsonPath("$.shipping.phone").value(TELEFONE_DO_CLIENTE))
                .andExpect(jsonPath("$.shipping.zipCode").value("01310100"))
                .andExpect(jsonPath("$.shipping.street").value("Rua Um"))
                .andExpect(jsonPath("$.shipping.number").value("100"))
                .andExpect(jsonPath("$.shipping.complement").value("Anexo 7, portão azul"))
                .andExpect(jsonPath("$.shipping.district").value("Centro"))
                .andExpect(jsonPath("$.shipping.city").value("Cidade"))
                .andExpect(jsonPath("$.shipping.uf").value("SP"));
    }

    

    





























    @Test
    void aEscalaDoFreteEhNormalizadaNaFonte() {
        assertThat(new ShippingProperties(Map.of("XX", new BigDecimal("20"))).byUf().get("XX")
                .scale())
                .as("ShippingProperties normaliza para escala 2 na LIGAÇÃO (achado K): é o que "
                        + "iguala o 201 e o 200 POR CONSTRUÇÃO. Escala 0 aqui significa que o "
                        + "conserto não está na fonte, e aí cada montador precisa lembrar de "
                        + "formatar — o arranjo que dura até o próximo montador")
                .isEqualTo(2);

        assertThat(new ShippingProperties(Map.of("XX", new BigDecimal("19.90"))).byUf().get("XX"))
                .as("e o gêmeo: normalizar não pode ALTERAR valor. Uma normalização que "
                        + "arredondasse mudaria dinheiro configurado em silêncio")
                .isEqualByComparingTo("19.90");
    }

    














    @Test
    void freteSemCentavoTemAMesmaEscalaEmTodaLeituraDoPedido() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.MG);
        adicionarAoCarrinho(cliente.token(), criarPeca("Bolsa", "60.00").productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(criacao);

        assertThat(corpoDe(criacao))
                .as("o 201 traz o frete com centavo mesmo com a configuração escrita como '20' "
                        + "— dinheiro em JSON sem escala vira \"shippingCost\":20, e '20' e "
                        + "'20.00' são o mesmo número e textos diferentes no extrato")
                .contains("\"shippingCost\":20.00");

        
        String retomada = corpoDe(mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(escalaDoFrete(retomada))
                .as("a retomada e a criação são o MESMO pedido: dois textos diferentes para o "
                        + "mesmo dinheiro é o contrato divergindo de si mesmo")
                .isEqualTo(escalaDoFrete(corpoDe(criacao)));

        String corpo = corpoDe(mockMvc.perform(lerPedido(cliente, orderId))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(corpo)
                .as("e o GET, que também vem de numeric(10,2), diz exatamente o mesmo")
                .contains("\"shippingCost\":20.00");
        assertThat(escalaDoFrete(corpo))
                .as("as TRÊS leituras comparadas como TEXTO e não como número: é a escala que "
                        + "diverge, e BigDecimal.compareTo não a enxerga")
                .isEqualTo(escalaDoFrete(corpoDe(criacao)));
    }

    

    private static String escalaDoFrete(String corpo) {
        return numeroNoCorpo(corpo, "shippingCost").toPlainString();
    }

    

    private String copyPasteGravado(UUID orderId) {
        return jdbc.queryForObject("SELECT pix_copy_paste FROM payments WHERE order_id = ?",
                String.class, orderId);
    }

    private String statusGravado(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?",
                String.class, orderId);
    }

    private Timestamp updatedAtGravado(UUID orderId) {
        return jdbc.queryForObject("SELECT updated_at FROM orders WHERE id = ?",
                Timestamp.class, orderId);
    }

    private String complementGravado(UUID orderId) {
        return jdbc.queryForObject("SELECT ship_complement FROM orders WHERE id = ?",
                String.class, orderId);
    }

    


    private List<String> ordemGravadaPorId(UUID orderId) {
        return jdbc.queryForList(
                "SELECT product_id FROM order_items WHERE order_id = ? ORDER BY id",
                String.class, orderId);
    }

    

    











    private static BigDecimal numeroNoCorpo(String corpo, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\":(-?\\d+(?:\\.\\d+)?)").matcher(corpo);
        assertThat(m.find()).as("campo %s ausente do corpo: %s", campo, corpo).isTrue();
        return new BigDecimal(m.group(1));
    }

    private static BigDecimal unitPriceNaResposta(String corpo, UUID productId) {
        return new BigDecimal(String.valueOf(umItem(corpo, productId, "unitPrice")));
    }

    private static String tituloNaResposta(String corpo, UUID productId) {
        return String.valueOf(umItem(corpo, productId, "title"));
    }

    


    private static Object umItem(String corpo, UUID productId, String campo) {
        List<Object> encontrados =
                ler(corpo, "$.items[?(@.productId=='" + productId + "')]." + campo);
        assertThat(encontrados)
                .as("exatamente um item do pedido para a peça %s", productId)
                .hasSize(1);
        return encontrados.get(0);
    }

    private static List<String> idsDosItens(String corpo) {
        return ler(corpo, "$.items[*].productId");
    }

    


    private static String codigoDe(String corpo) {
        return corpo == null || corpo.isBlank() ? null : ler(corpo, "$.code");
    }

    













    private static <T> T ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

    private static String corpoDe(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString();
    }

    private static UUID idDoPedido(MvcResult resultado) throws Exception {
        return UUID.fromString(ler(corpoDe(resultado), "$.id"));
    }

    

    private static final String TELEFONE_DO_CLIENTE = "11987654321";

    private record Cliente(String token, UUID addressId, UUID userId) {}

    private record Peca(UUID productId, String titulo, UUID categoryId, CustomUserDetails dona) {}

    private RequestBuilder checkout(Cliente cliente) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + cliente.addressId() + "\"}");
    }

    

    private RequestBuilder lerPedido(Cliente cliente, UUID orderId) {
        return get("/me/orders/{id}", orderId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token());
    }

    private Cliente novoClienteComEndereco(UfBrasileira uf) throws Exception {
        return novoClienteComEndereco(uf, null);
    }

    

    private Cliente novoClienteComEndereco(UfBrasileira uf, String complemento) throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "pedido-" + m + "@teste.local";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"%s","password":"senha-forte-123",
                                 "phone":"%s"}"""
                                .formatted(m, email, TELEFONE_DO_CLIENTE)))
                .andExpect(status().isCreated());

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = ler(login, "$.accessToken");

        String complementoJson = complemento == null ? "null" : "\"" + complemento + "\"";
        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"Rua Um","number":"100",
                                 "complement":%s,"district":"Centro","city":"Cidade",
                                 "uf":"%s"}""".formatted(complementoJson, uf.name())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        return new Cliente(token, UUID.fromString(ler(endereco, "$.id")), userId);
    }

    


    private Peca criarPeca(String titulo, String preco) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        String tituloCompleto = titulo + " " + m;
        Product produto = productRepository.save(Product.create(dona, categoria, tituloCompleto,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal(preco)));

        return new Peca(produto.getId(), tituloCompleto, categoria.getId(),
                new CustomUserDetails(dona));
    }

    private void adicionarAoCarrinho(String token, UUID productId) throws Exception {
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isOk());
    }

    





    private void editarPeca(Peca peca, String preco, String titulo) throws Exception {
        mockMvc.perform(put("/seller/products/{id}", peca.productId())
                        .with(user(peca.dona()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"desc","brand":"Marca","color":"azul",
                                 "size":"M","sizeLabel":null,"condition":"GOOD","price":%s,
                                 "categoryId":"%s"}"""
                                .formatted(titulo, preco, peca.categoryId())))
                .andExpect(status().isOk());
    }
}
