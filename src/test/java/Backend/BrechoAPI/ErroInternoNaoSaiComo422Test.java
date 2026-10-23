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
import Backend.BrechoAPI.service.CartService;
import Backend.BrechoAPI.service.CheckoutTransactionService;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;







































































































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ErroInternoNaoSaiComo422Test {

    






















































































































































    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    






    @TestConfiguration
    static class AndaimesCegos {

        static final String USER_REPOSITORY_REAL = "userRepository";
        static final String PRODUCT_REPOSITORY_REAL = "productRepository";

        



        @Bean
        @Primary
        UserRepository userRepositoryComFindByIdCego(BeanFactory beans) {
            InvocationHandler cego = (proxy, metodo, args) -> {
                if ("findById".equals(metodo.getName())) {
                    return Optional.empty();
                }
                return delegar(beans.getBean(USER_REPOSITORY_REAL, UserRepository.class), metodo, args);
            };
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class}, cego);
        }

        


        @Bean
        @Primary
        ProductRepository productRepositoryComFindAllByIdCego(BeanFactory beans) {
            InvocationHandler cego = (proxy, metodo, args) -> {
                if ("findAllById".equals(metodo.getName())) {
                    return List.of();
                }
                return delegar(beans.getBean(PRODUCT_REPOSITORY_REAL, ProductRepository.class), metodo, args);
            };
            return (ProductRepository) Proxy.newProxyInstance(
                    ProductRepository.class.getClassLoader(), new Class<?>[]{ProductRepository.class}, cego);
        }

        private static Object delegar(Object real, Method metodo, Object[] args) throws Throwable {
            try {
                return metodo.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired UserRepository userRepository;           
    @Autowired ProductRepository productRepository;     
    @Autowired CategoryRepository categoryRepository;
    @Autowired CheckoutTransactionService checkoutTransactionService;
    @Autowired CartService cartService;

    

    





    @Test
    void osDoisAndaimesDesteContextoEstaoMesmoCegos() throws Exception {
        Cliente cliente = novoCliente();
        assertThat(linhasEm("users", cliente.userId()))
                .as("o registro tem que ter gravado o usuário; sem a linha, findById vazio "
                        + "seria verdade trivial e o sítio 1 nunca seria a condição testada")
                .isEqualTo(1);
        assertThat(userRepository.findById(cliente.userId()))
                .as("findById tem que estar cego — é o que faz o usuário AUTENTICADO (o filtro "
                        + "carrega por e-mail, real) sumir exatamente na consulta do sítio 1. Se "
                        + "isto aparecer, os testes do sítio 1 deixaram de exercitar o sítio")
                .isEmpty();

        UUID peca = criarPeca("50.00");
        assertThat(linhasEm("products", peca))
                .as("a peça tem que existir — a FK de cart_items depende dela, e lista vazia "
                        + "sem linha no banco seria vacuidade")
                .isEqualTo(1);
        assertThat(productRepository.findAllById(List.of(peca)))
                .as("findAllById tem que estar cego — é o que faz o produto do item de carrinho "
                        + "sumir do mapa do getCart com a FK intacta (sítio 3)")
                .isEmpty();
    }

    

    



















    @Test
    void sitio1_usuarioAutenticadoQueSumiuDoBancoNaoSaiComo422ComOUuidNoCorpo() throws Exception {
        Cliente cliente = novoCliente();

        MvcResult resposta = mockMvc.perform(post("/me/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + UUID.randomUUID() + "\"}"))
                .andReturn();
        String corpo = corpoDe(resposta);

        assertThat(resposta.getResponse().getStatus())
                .as("usuário autenticado que o banco não encontra é defeito NOSSO (o token é "
                        + "válido, o filtro o carregou por e-mail): a resposta é 5xx. 422 aqui é "
                        + "o sítio 1 vivo — IllegalStateException caindo em handleDomainRule, "
                        + "culpa do cliente para um erro interno. Corpo: %s", corpo)
                .isBetween(500, 599);

        String code = ler(corpo, "$.code");
        assertThat(code)
                .as("5xx sem $.code está fora do formato único (ApiError) — a exceção não "
                        + "chegou a handler nenhum. A categoria de erro nosso sai COM código. "
                        + "Corpo: %s", corpo)
                .isNotNull();
        assertThat(code)
                .as("DOMAIN_RULE_VIOLATION é o código das regras de domínio que o CLIENTE "
                        + "violou; vesti-lo num defeito nosso é mentir a culpa. Corpo: %s", corpo)
                .isNotEqualTo("DOMAIN_RULE_VIOLATION");

        assertThat(corpo)
                .as("o uuid do usuário é texto interno — vazamento de informação para quem "
                        + "está do lado de fora (decisão do Pedro, 07/10-c, Parte A item 7). "
                        + "Corpo: %s", corpo)
                .doesNotContain(cliente.userId().toString());
        assertThat(corpo)
                .as("o literal do sítio não sai no corpo — o diagnóstico muda de endereço "
                        + "(log/exceção), não de dono. Corpo: %s", corpo)
                .doesNotContain("usuário autenticado não encontrado");
    }

    

    













    @Test
    void sitio1_gemeoDePresenca_oUuidViveNoDiagnosticoNaoNoCorpo() throws Exception {
        Cliente cliente = novoCliente();

        Diagnostico diagnostico = provocarCapturandoLog(() ->
                checkoutTransactionService.createOrder(cliente.userId(), UUID.randomUUID(), clock.instant()));

        assertThat(diagnostico.falha())
                .as("a condição provocada tem que falhar — um retorno normal com o usuário "
                        + "sumido do banco seria defeito bem pior que o 422")
                .isNotNull();
        assertThat(diagnostico.texto())
                .as("o uuid do usuário tem que SOBREVIVER como diagnóstico (mensagem/causa da "
                        + "exceção, ou log no ponto de lançamento). Se sumiu de todo lugar, a "
                        + "asserção de ausência do item 1 passou por apagamento, não por "
                        + "realojamento. Diagnóstico capturado:%n%s", diagnostico.texto())
                .contains(cliente.userId().toString());
    }

    

    












    @Test
    void sitio3_produtoDoItemDeCarrinhoQueSumiuNaoSaiComo422ComOLiteralNoCorpo() throws Exception {
        Cliente cliente = novoCliente();
        colocarNoCarrinhoPorJdbc(cliente, criarPeca("70.00"));

        MvcResult resposta = mockMvc.perform(get("/me/cart")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andReturn();
        String corpo = corpoDe(resposta);

        assertThat(resposta.getResponse().getStatus())
                .as("produto que a FK garante e o mapa não tem é defeito NOSSO (leitura "
                        + "inconsistente), não regra violada pelo cliente: 5xx. 422 aqui é o "
                        + "sítio 3 vivo. Corpo: %s", corpo)
                .isBetween(500, 599);

        String code = ler(corpo, "$.code");
        assertThat(code)
                .as("5xx sem $.code = fora do formato único; a categoria de erro nosso sai "
                        + "com código. Corpo: %s", corpo)
                .isNotNull();
        assertThat(code)
                .as("não é DOMAIN_RULE_VIOLATION — o cliente não violou regra nenhuma lendo "
                        + "o próprio carrinho. Corpo: %s", corpo)
                .isNotEqualTo("DOMAIN_RULE_VIOLATION");

        assertThat(corpo)
                .as("o literal do sítio é texto interno; não sai no corpo. Corpo: %s", corpo)
                .doesNotContain("produto referenciado pelo item de carrinho");
    }

    

    






    @Test
    void sitio3_gemeoDePresenca_oLiteralViveNoDiagnosticoNaoNoCorpo() throws Exception {
        Cliente cliente = novoCliente();
        colocarNoCarrinhoPorJdbc(cliente, criarPeca("70.00"));

        Diagnostico diagnostico = provocarCapturandoLog(() -> cartService.getCart(cliente.userId()));

        assertThat(diagnostico.falha())
                .as("a condição provocada tem que falhar — um carrinho respondido 'normal' com "
                        + "item cujo produto sumiu do mapa seria resposta inventada")
                .isNotNull();
        assertThat(diagnostico.texto())
                .as("o literal do sítio tem que sobreviver como diagnóstico interno. "
                        + "Diagnóstico capturado:%n%s", diagnostico.texto())
                .contains("produto referenciado pelo item de carrinho não existe");
    }

    

    








    @Test
    void controleDaDv5_illegalArgumentDeliberadoContinua422ComAMensagem() throws Exception {
        MvcResult resposta = mockMvc.perform(get("/products")
                        .param("minPrice", "100").param("maxPrice", "50"))
                .andReturn();
        String corpo = corpoDe(resposta);

        assertThat(resposta.getResponse().getStatus())
                .as("o IllegalArgumentException DELIBERADO da DV5 continua 422 — se isto caiu, "
                        + "a DV8 alterou a régua da DV5 de carona. Corpo: %s", corpo)
                .isEqualTo(422);
        assertThat(ErroInternoNaoSaiComo422Test.<String>ler(corpo, "$.code"))
                .as("e continua DOMAIN_RULE_VIOLATION. Corpo: %s", corpo)
                .isEqualTo("DOMAIN_RULE_VIOLATION");
        assertThat(ErroInternoNaoSaiComo422Test.<String>ler(corpo, "$.message"))
                .as("com a mensagem deliberada — ela é contrato com o cliente, não vazamento. "
                        + "Corpo: %s", corpo)
                .isEqualTo("minPrice não pode ser maior que maxPrice");
    }

    

    private record Cliente(String token, UUID userId) {}

    private record Diagnostico(Throwable falha, String texto) {}

    






    private Diagnostico provocarCapturandoLog(ThrowingCallable acao) {
        ch.qos.logback.classic.Logger raiz = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captura = new ListAppender<>();
        captura.start();
        raiz.addAppender(captura);
        Throwable falha;
        try {
            falha = catchThrowable(acao);
        } finally {
            raiz.detachAppender(captura);
            captura.stop();
        }

        StringBuilder texto = new StringBuilder();
        int profundidade = 0;
        for (Throwable t = falha; t != null && profundidade < 10; t = t.getCause(), profundidade++) {
            texto.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        }
        for (ILoggingEvent evento : captura.list) {
            texto.append(evento.getFormattedMessage()).append('\n');
            profundidade = 0;
            for (IThrowableProxy p = evento.getThrowableProxy(); p != null && profundidade < 10;
                    p = p.getCause(), profundidade++) {
                texto.append(p.getClassName()).append(": ").append(p.getMessage()).append('\n');
            }
        }
        return new Diagnostico(falha, texto.toString());
    }

    



    private Cliente novoCliente() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "dv8-" + m + "@teste.local";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cliente %s","email":"%s","password":"senha-forte-123",
                                 "phone":"11987654321"}""".formatted(m, email)))
                .andExpect(status().isCreated());

        String login = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        return new Cliente(ler(login, "$.accessToken"), userId);
    }

    

    private UUID criarPeca(String preco) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-dv8-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-dv8-" + m, CategorySizeType.ALPHA));
        Product produto = productRepository.save(Product.create(dona, categoria, "Peça " + m,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal(preco)));
        return produto.getId();
    }

    


    private void colocarNoCarrinhoPorJdbc(Cliente cliente, UUID productId) {
        UUID cartId = jdbc.queryForObject(
                "SELECT id FROM carts WHERE user_id = ?", UUID.class, cliente.userId());
        jdbc.update("INSERT INTO cart_items (id, cart_id, product_id, created_at, updated_at)"
                        + " VALUES (?, ?, ?, now(), now())",
                UUID.randomUUID(), cartId, productId);
    }

    private int linhasEm(String tabela, UUID id) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM " + tabela + " WHERE id = ?", Integer.class, id);
        return total == null ? 0 : total;
    }

    private static String corpoDe(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString();
    }

    


    private static <T> T ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);
}
