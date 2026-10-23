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
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.ShippingService;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
















































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CongelamentoDoPedidoTest {

    

































































































































































































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
                    "RJ", new BigDecimal("24.90")));
        }
    }

    




    private static final BigDecimal FRETE_SP = new BigDecimal("19.90");

    


    private static final BigDecimal FRETE_REAJUSTADO = new BigDecimal("99.90");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    












    @MockitoSpyBean ProductRepository productRepository;

    










    @MockitoSpyBean ShippingService shippingService;

    

    













    @Test
    void aTabelaDeFreteDesteTesteEstaMesmoNoAr() {
        assertThat(shippingService.costFor(UfBrasileira.SP))
                .as("o bean @Primary deste teste diz 19.90 para SP; se vier outro valor, a "
                        + "substituição não aconteceu e todo total deste arquivo está sendo "
                        + "comparado contra a tabela errada")
                .isEqualByComparingTo(FRETE_SP);

        assertThat(catchThrowable(() -> shippingService.costFor(UfBrasileira.AC)))
                .as("AC está fora da tabela deste teste: se não recusar, o spy não está "
                        + "delegando ao ShippingService real")
                .isInstanceOf(DomainRuleException.class);
    }

    

    





















    @Test
    void mudarOPrecoDaPecaNaoAlteraOPedido() throws Exception {
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
        String corpoDaCriacao = corpoDe(criacao);

        
        assertThat(corpoDaCriacao).contains("\"subtotal\":130.00");
        assertThat(corpoDaCriacao).contains("\"total\":149.90");

        
        editarPeca(cara, "500.00", cara.titulo());

        
        
        
        mockMvc.perform(get("/products/{id}", cara.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(500.00));

        
        assertThat(unitPriceGravado(orderId, cara.productId()))
                .as("order_items.unit_price é CÓPIA, não join: o relatório de venda antiga "
                        + "nunca vai a products para precificar")
                .isEqualByComparingTo("100.00");
        assertThat(unitPriceGravado(orderId, barata.productId()))
                .as("a peça que ninguém tocou também não pode ter mudado")
                .isEqualByComparingTo("30.00");
        assertThat(subtotalGravado(orderId))
                .as("orders.subtotal continua a soma do instante da compra")
                .isEqualByComparingTo("130.00");
        assertThat(somaDosItensCongelados(orderId))
                .as("O GÊMEO ARITMÉTICO, no banco: o subtotal do pedido é EXATAMENTE a soma "
                        + "dos unit_price congelados. Um subtotal recalculado sobre os preços "
                        + "de hoje daria 530.00 e o pedido deixaria de fechar consigo mesmo")
                .isEqualByComparingTo(subtotalGravado(orderId));

        
        MvcResult retomada = mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andReturn();
        String corpo = corpoDe(retomada);

        assertThat(idDoPedido(retomada))
                .as("a retomada tem que ser O MESMO pedido; outro id significaria que a "
                        + "leitura acima nem é do pedido que está sendo conferido")
                .isEqualTo(orderId);

        assertThat(unitPriceNaResposta(corpo, cara.productId()))
                .as("a resposta da retomada tem que trazer o preço CONGELADO (100.00); 500.00 "
                        + "significa que a montagem leu o Product atual — run S1")
                .isEqualByComparingTo("100.00");
        assertThat(unitPriceNaResposta(corpo, barata.productId()))
                .isEqualByComparingTo("30.00");

        assertThat(corpo)
                .as("o subtotal da retomada tem que ser a soma CONGELADA, com centavo — um "
                        + "recálculo sobre os preços de hoje daria 530.00 (run S3)")
                .contains("\"subtotal\":130.00");
        assertThat(corpo)
                .as("e o total acompanha: 130.00 + 19.90")
                .contains("\"total\":149.90");
        assertThat(corpo)
                .as("500.00 não pode aparecer em lugar nenhum do pedido — nem em item, nem "
                        + "em subtotal, nem em total")
                .doesNotContain("500.00");

        
        
        assertThat(numeroNoCorpo(corpo, "total"))
                .isEqualByComparingTo(numeroNoCorpo(corpo, "subtotal")
                        .add(numeroNoCorpo(corpo, "shippingCost")));
    }

    

    













    @Test
    void mudarOTituloDaPecaNaoAlteraOPedido() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca peca = criarPeca("Vestido como a cliente comprou", "80.00");
        adicionarAoCarrinho(cliente.token(), peca.productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(criacao);

        String tituloNovo = "Vestido REBATIZADO depois da compra";
        editarPeca(peca, "80.00", tituloNovo);

        
        mockMvc.perform(get("/products/{id}", peca.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(tituloNovo));

        assertThat(tituloGravado(orderId, peca.productId()))
                .as("order_items.title é cópia: o pedido não faz join em products para se "
                        + "nomear")
                .isEqualTo(peca.titulo());

        MvcResult retomada = mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andReturn();
        String corpo = corpoDe(retomada);

        assertThat(tituloNaResposta(corpo, peca.productId()))
                .as("o pedido tem que continuar mostrando o título de quando a cliente "
                        + "comprou — run S2")
                .isEqualTo(peca.titulo());
        assertThat(corpo)
                .as("o nome novo não pode vazar para lugar nenhum da resposta do pedido")
                .doesNotContain(tituloNovo);
    }

    

    























    @Test
    void oFreteFicaGuardadoNaLinhaDoPedido() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("Bolsa", "60.00").productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(criacao);

        assertThat(corpoDe(criacao))
                .as("o frete do instante, com centavo, vindo da tabela deste teste")
                .contains("\"shippingCost\":19.90");
        assertThat(freteGravado(orderId))
                .as("e ele está numa COLUNA do pedido — sem coluna, não há o que congelar")
                .isEqualByComparingTo(FRETE_SP);

        
        doReturn(FRETE_REAJUSTADO).when(shippingService).costFor(any());

        
        
        assertThat(shippingService.costFor(UfBrasileira.SP))
                .as("a tabela em vigor AGORA diz 99.90 para SP; se ainda disser 19.90, este "
                        + "teste não está exercitando reajuste nenhum")
                .isEqualByComparingTo(FRETE_REAJUSTADO);

        
        
        
        clearInvocations(shippingService);

        MvcResult retomada = mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andReturn();
        String corpo = corpoDe(retomada);

        assertThat(corpo)
                .as("o frete da retomada tem que vir da COLUNA orders.shipping_cost; 99.90 "
                        + "significa recálculo pela tabela de hoje — run S4")
                .contains("\"shippingCost\":19.90");
        assertThat(corpo)
                .as("60.00 + 19.90: o total também é feito das parcelas congeladas")
                .contains("\"total\":79.90");
        assertThat(freteGravado(orderId))
                .as("e a coluna continua intacta depois de tudo")
                .isEqualByComparingTo(FRETE_SP);

        verify(shippingService, never()).costFor(any());
        
        
        
        
    }

    






















































































    @Test
    void retomadaFuncionaMesmoSeAUfSaiuDaTabelaDeFrete() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("Casaco", "80.00").productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(criacao);
        assertThat(corpoDe(criacao)).contains("\"shippingCost\":19.90");

        
        doThrow(new DomainRuleException(ErrorCode.SHIPPING_UNAVAILABLE_FOR_UF,
                "não entregamos em SP"))
                .when(shippingService).costFor(any());

        
        
        assertThat(catchThrowable(() -> shippingService.costFor(UfBrasileira.SP)))
                .as("se SP ainda tiver frete, este teste não está exercitando UF nenhuma "
                        + "fora da tabela")
                .isInstanceOf(DomainRuleException.class);

        clearInvocations(shippingService);

        MvcResult retomada = mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(idDoPedido(retomada))
                .as("é o MESMO pedido: 200 com id novo seria pedido duplicado com cara de "
                        + "idempotência")
                .isEqualTo(orderId);
        assertThat(corpoDe(retomada))
                .as("o frete vem da COLUNA congelada, não de uma tabela que hoje nem responde "
                        + "por SP")
                .contains("\"shippingCost\":19.90");
        assertThat(freteGravado(orderId)).isEqualByComparingTo(FRETE_SP);

        verify(shippingService, never()).costFor(any());
        
        
        
        
        
    }

    

    





































































































    














































































    @Test
    void retomadaNaoRelePrecoDeProduct() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca cara = criarPeca("Casaco reajustado", "100.00");
        Peca barata = criarPeca("Lenço arquivado", "30.00");
        adicionarAoCarrinho(cliente.token(), cara.productId());
        adicionarAoCarrinho(cliente.token(), barata.productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(criacao);
        assertThat(corpoDe(criacao)).contains("\"subtotal\":130.00");

        
        editarPeca(cara, "500.00", cara.titulo());

        
        mockMvc.perform(get("/products/{id}", cara.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(500.00));

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        List<Product> entregues = new ArrayList<>();
        Answer<?> delegacaoAoRepositorioReal =
                mockingDetails(productRepository).getMockCreationSettings().getDefaultAnswer();
        Runnable embrulhar = () -> doAnswer(invocacao -> {
            @SuppressWarnings("unchecked")
            Optional<Product> real = (Optional<Product>) delegacaoAoRepositorioReal.answer(invocacao);
            return real.map(produto -> {
                Product espiao = spy(produto);
                entregues.add(espiao);
                return espiao;
            });
        }).when(productRepository).findByIdAndDeletedAtIsNull(any());
        Runnable desembrulhar = () -> doAnswer(delegacaoAoRepositorioReal)
                .when(productRepository).findByIdAndDeletedAtIsNull(any());

        embrulhar.run();
        clearInvocations(productRepository);

        
        MvcResult retomada = mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andReturn();
        String corpo = corpoDe(retomada);

        assertThat(idDoPedido(retomada)).isEqualTo(orderId);
        assertThat(unitPriceNaResposta(corpo, cara.productId()))
                .as("o VALOR congelado, como guarda barata — quem prova isto de verdade é "
                        + "mudarOPrecoDaPecaNaoAlteraOPedido; aqui ele só mostra que a "
                        + "sabotagem de caminho abaixo não é a mesma coisa que a de valor")
                .isEqualByComparingTo("100.00");
        assertThat(corpo).contains("\"subtotal\":130.00");

        
        assertThat(entregues)
                .as("a checagem A da retomada tem que ter consultado as DUAS peças do pedido "
                        + "e recebido as duas de volta; zero significa que a checagem não "
                        + "roda na retomada (e o never() abaixo estaria afirmando sobre nada)")
                .hasSize(2);

        
        
        
        
        for (Product entregue : entregues) {
            verify(entregue, never()).getPrice();
            verify(entregue, never()).getTitle();
        }

        
        
        
        verify(productRepository, times(2)).findByIdAndDeletedAtIsNull(any());
        verifyNoMoreInteractions(productRepository);
        desembrulhar.run();

        
        mockMvc.perform(delete("/seller/products/{id}", barata.productId())
                        .with(user(barata.dona())))
                .andExpect(status().isNoContent());

        
        
        mockMvc.perform(get("/products/{id}", barata.productId()))
                .andExpect(status().isNotFound());

        entregues.clear();
        embrulhar.run();
        clearInvocations(productRepository);

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"));

        
        
        
        
        
        verify(productRepository, atLeastOnce()).findByIdAndDeletedAtIsNull(any());
        verify(productRepository, atMost(2)).findByIdAndDeletedAtIsNull(any());
        verifyNoMoreInteractions(productRepository);
        for (Product entregue : entregues) {
            verify(entregue, never()).getPrice();
            verify(entregue, never()).getTitle();
        }
        desembrulhar.run();

        assertThat(subtotalGravado(orderId))
                .as("nem a retomada aceita nem a recusada reescreveram o congelado")
                .isEqualByComparingTo("130.00");
    }

    

    





























    @Test
    void totalEhSubtotalMaisFrete() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("Saia", "70.00").productId());
        adicionarAoCarrinho(cliente.token(), criarPeca("Camisa", "30.50").productId());
        adicionarAoCarrinho(cliente.token(), criarPeca("Broche", "12.25").productId());

        MvcResult criacao = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn();
        UUID orderId = idDoPedido(criacao);
        String corpo = corpoDe(criacao);

        assertThat(corpo).contains("\"subtotal\":112.75");
        assertThat(corpo).contains("\"shippingCost\":19.90");
        assertThat(corpo)
                .as("total = 112.75 + 19.90. Um total() que devolvesse o subtotal daria "
                        + "112.75 e passaria em todos os outros testes deste arquivo")
                .contains("\"total\":132.65");

        BigDecimal subtotal = numeroNoCorpo(corpo, "subtotal");
        BigDecimal frete = numeroNoCorpo(corpo, "shippingCost");
        BigDecimal total = numeroNoCorpo(corpo, "total");

        assertThat(total).isEqualByComparingTo(subtotal.add(frete));
        assertThat(total)
                .as("o gêmeo do gêmeo: total IGUAL ao subtotal seria um frete perdido no "
                        + "caminho, e o número ainda pareceria plausível")
                .isNotEqualByComparingTo(subtotal);
        assertThat(total).isNotEqualByComparingTo(frete);

        
        
        assertThat(subtotalGravado(orderId)).isEqualByComparingTo("112.75");
        assertThat(freteGravado(orderId)).isEqualByComparingTo(FRETE_SP);
        assertThat(somaDosItensCongelados(orderId))
                .as("subtotal é a soma dos unit_price congelados, não um número escrito por "
                        + "outro caminho")
                .isEqualByComparingTo(subtotalGravado(orderId));
        assertThat(subtotalGravado(orderId).add(freteGravado(orderId)))
                .as("o total da resposta é derivado DESTAS duas colunas")
                .isEqualByComparingTo(total);

        
        assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM information_schema.columns
                         WHERE table_name = 'orders' AND column_name = 'total'""", Integer.class))
                .as("orders NÃO tem coluna total (decisão 5): duas representações do mesmo "
                        + "fato é a Regra 1 sendo violada, e a divergência entre elas não "
                        + "teria juiz")
                .isZero();
    }

    

    private BigDecimal unitPriceGravado(UUID orderId, UUID productId) {
        return jdbc.queryForObject(
                "SELECT unit_price FROM order_items WHERE order_id = ? AND product_id = ?",
                BigDecimal.class, orderId, productId);
    }

    private String tituloGravado(UUID orderId, UUID productId) {
        return jdbc.queryForObject(
                "SELECT title FROM order_items WHERE order_id = ? AND product_id = ?",
                String.class, orderId, productId);
    }

    private BigDecimal subtotalGravado(UUID orderId) {
        return jdbc.queryForObject("SELECT subtotal FROM orders WHERE id = ?",
                BigDecimal.class, orderId);
    }

    private BigDecimal freteGravado(UUID orderId) {
        return jdbc.queryForObject("SELECT shipping_cost FROM orders WHERE id = ?",
                BigDecimal.class, orderId);
    }

    private BigDecimal somaDosItensCongelados(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(unit_price), 0) FROM order_items WHERE order_id = ?",
                BigDecimal.class, orderId);
    }

    

    










    private static BigDecimal numeroNoCorpo(String corpo, String campo) {
        Matcher m = Pattern.compile("\"" + campo + "\":(-?\\d+(?:\\.\\d+)?)").matcher(corpo);
        assertThat(m.find()).as("campo %s ausente do corpo: %s", campo, corpo).isTrue();
        return new BigDecimal(m.group(1));
    }

    private static BigDecimal unitPriceNaResposta(String corpo, UUID productId) {
        return new BigDecimal(String.valueOf(
                umItem(corpo, productId, "unitPrice")));
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

    

    private Cliente novoClienteComEndereco(UfBrasileira uf) throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "congelamento-" + m + "@teste.local";

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

        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"Rua Um","number":"100",
                                 "complement":null,"district":"Centro","city":"Cidade",
                                 "uf":"%s"}""".formatted(uf.name())))
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
