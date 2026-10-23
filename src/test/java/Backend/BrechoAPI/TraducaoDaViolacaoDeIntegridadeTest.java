package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.OrderStatus;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.OrderRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
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
import org.springframework.test.web.servlet.RequestBuilder;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


















































































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TraducaoDaViolacaoDeIntegridadeTest {

    





















































































































































    







































































































































































































































































































    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    



































    @TestConfiguration
    static class Passo2CegoAoPedidoAberto {

        



        static final String REPOSITORIO_REAL = "orderRepository";

        @Bean
        @Primary
        OrderRepository orderRepositoryCegoAoPedidoAberto(BeanFactory beans) {
            InvocationHandler cego = (proxy, metodo, args) -> {
                if ("findByUserIdAndStatus".equals(metodo.getName())) {
                    return Optional.empty();
                }
                return delegar(beans.getBean(REPOSITORIO_REAL, OrderRepository.class), metodo, args);
            };
            return (OrderRepository) Proxy.newProxyInstance(
                    OrderRepository.class.getClassLoader(),
                    new Class<?>[]{OrderRepository.class},
                    cego);
        }

        



        private static Object delegar(OrderRepository real, Method metodo, Object[] args)
                throws Throwable {
            try {
                return metodo.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    



























    @TestConfiguration
    static class PreChecagemDeEmailCega {

        


        static final String REPOSITORIO_REAL = "userRepository";

        @Bean
        @Primary
        UserRepository userRepositoryComPreChecagemDeEmailCega(BeanFactory beans) {
            InvocationHandler cego = (proxy, metodo, args) -> {
                if ("existsByEmail".equals(metodo.getName())) {
                    return false;
                }
                return delegar(beans.getBean(REPOSITORIO_REAL, UserRepository.class), metodo, args);
            };
            return (UserRepository) Proxy.newProxyInstance(
                    UserRepository.class.getClassLoader(),
                    new Class<?>[]{UserRepository.class},
                    cego);
        }

        

        private static Object delegar(UserRepository real, Method metodo, Object[] args)
                throws Throwable {
            try {
                return metodo.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepository orderRepository;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;

    

    












    @Test
    void oPasso2DesteContextoEstaMesmoCegoAoPedidoAberto() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente, criarPeca("70.00"));

        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        assertThat(abertosDe(cliente))
                .as("o primeiro checkout tem que ter gravado uma linha AWAITING_PAYMENT; "
                        + "sem ela não há o que o INSERT do segundo POST conflite")
                .isEqualTo(1);

        assertThat(orderRepository.findByUserIdAndStatus(cliente.userId(), OrderStatus.AWAITING_PAYMENT))
                .as("o pedido aberto tem que estar INVISÍVEL para a consulta do passo 2 — é "
                        + "assim que o READ COMMITTED de uma corrida real o esconde. Se ele "
                        + "aparecer, o segundo POST para no passo 2 e o catch do passo 8 "
                        + "nunca roda: o teste do 23505 fica verde sem exercitar nada")
                .isEmpty();
    }

    

    
































    @Test
    void violacaoDoIndiceDePedidoAbertoNoInsertVira409OrderAlreadyOpen() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente, criarPeca("70.00"));

        MvcResult primeiro = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID pedidoAberto = UUID.fromString(ler(corpoDe(primeiro), "$.id"));

        
        
        MvcResult segundo = mockMvc.perform(checkout(cliente)).andReturn();
        String corpo = corpoDe(segundo);

        assertThat(segundo.getResponse().getStatus())
                .as("a violação de uk_orders_awaiting_per_user tem que sair como conflito de "
                        + "negócio, não como defeito. 500 aqui significa que o catch do passo 8 "
                        + "não reconheceu a violação — é o que acontece com o discriminador por "
                        + "TIPO (catch DuplicateKeyException), porque por JPA ela chega como "
                        + "DataIntegrityViolationException pura. Corpo: %s", corpo)
                .isEqualTo(409);

        assertThat(TraducaoDaViolacaoDeIntegridadeTest.<String>ler(corpo, "$.code"))
                .as("status sozinho não distingue nada: UNIQUENESS_VIOLATION (o handler "
                        + "genérico, quando o INSERT escapa do try por causa de save() sem "
                        + "flush) também é 409. Só o $.code separa 'você já tem um pedido "
                        + "aberto' de 'operação viola uma restrição de unicidade'. Corpo: %s", corpo)
                .isEqualTo("ORDER_ALREADY_OPEN");

        String mensagem = ler(corpo, "$.message");
        assertThat(mensagem)
                .as("o 409 veio do passo 2 (consulta), não do passo 8 (INSERT): a mensagem cita "
                        + "o id do pedido aberto, e o catch do passo 8 não tem esse id em mãos. "
                        + "Se esta asserção cair, o andaime que cega a consulta parou de "
                        + "funcionar e este teste deixou de exercitar o catch")
                .doesNotContain(pedidoAberto.toString());

        assertThat(pedidosDe(cliente))
                .as("o INSERT recusado não pode ter deixado linha: a transação inteira volta")
                .isEqualTo(1);
    }

    

    

































    @Test
    void violacaoQueNaoEhAUnicidadeDoPedidoAbertoVira500PersistenceFailure() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        adicionarAoCarrinho(cliente, criarPeca("99999999.99"));
        adicionarAoCarrinho(cliente, criarPeca("99999999.99"));

        MvcResult resposta = mockMvc.perform(checkout(cliente)).andReturn();
        String corpo = corpoDe(resposta);

        assertThat(resposta.getResponse().getStatus())
                .as("estouro de numeric(10,2) no INSERT NÃO é conflito do cliente: é defeito. "
                        + "409 aqui significa catch largo — o cliente recebe 'você já tem um "
                        + "pedido aberto' para uma violação que não tem nada a ver com isso, e "
                        + "é mandado tentar de novo numa situação em que tentar de novo nunca "
                        + "funciona. Corpo: %s", corpo)
                .isEqualTo(500);

        assertThat(TraducaoDaViolacaoDeIntegridadeTest.<String>ler(corpo, "$.code"))
                .as("500 sem $.code significa que a exceção não chegou a @ExceptionHandler "
                        + "nenhum — é o que acontece quando a violação escapa do try (save() "
                        + "sem flush) ou quando alguém lança algo que o handler não cobre. "
                        + "Corpo: %s", corpo)
                .isEqualTo("PERSISTENCE_FAILURE");

        String mensagem = ler(corpo, "$.message");
        assertThat(mensagem)
                .as("a mensagem do cliente é fixa e diz o que ele pode fazer")
                .isNotBlank();
        assertThat(mensagem)
                .as("texto de violação do Postgres carrega nome de tabela, de coluna e de "
                        + "constraint; a causa vai INTEIRA para o log, e nada dela para o corpo")
                .doesNotContain("orders", "subtotal", "numeric", "constraint", "uk_", "ERROR");
        assertThat(corpo)
                .as("nenhum nome de constraint nem texto cru do driver em lugar nenhum da "
                        + "resposta")
                .doesNotContain("uk_", "ConstraintViolation", "overflow");

        assertThat(pedidosDe(cliente))
                .as("nada é escrito quando o INSERT falha: a transação volta inteira")
                .isZero();
    }

    

    











































    @Test
    void violacaoQueNaoEhDeUnicidadeAlcancandoARedeDeSegurancaVira500PersistenceFailure()
            throws Exception {
        Cliente cliente = novoClienteComEndereco();
        String plantacao = "qa_dv10_" + UUID.randomUUID().toString().substring(0, 8);

        MvcResult comPlantacao;
        int enderecosAposRecusa;
        plantarCheckQueRecusaEnderecoNovoDe(cliente, plantacao);
        try {
            comPlantacao = mockMvc.perform(novoEndereco(cliente)).andReturn();
            enderecosAposRecusa = enderecosDe(cliente);
        } finally {
            desfazerPlantacao(plantacao);
        }
        MvcResult gemeoInLoco = mockMvc.perform(novoEndereco(cliente)).andReturn();

        String corpo = corpoDe(comPlantacao);
        assertThat(comPlantacao.getResponse().getStatus())
                .as("um CHECK recusado (23514) no INSERT de addresses é erro NOSSO alcançando "
                        + "a rede de segurança: 500 PERSISTENCE_FAILURE (ADR 0018 §2.1). 409 "
                        + "aqui é o handler afirmando 'unicidade' sem prova — culpa do cliente "
                        + "com causa falsa, mandando tentar de novo onde tentar de novo nunca "
                        + "funciona. Corpo: %s", corpo)
                .isEqualTo(500);

        assertThat(TraducaoDaViolacaoDeIntegridadeTest.<String>ler(corpo, "$.code"))
                .as("status sozinho não basta: 500 sem $.code significa que a exceção não "
                        + "chegou a @ExceptionHandler nenhum. O ramo não-23505 da rede responde "
                        + "com o rótulo geral da família de culpa nossa (Javadoc de ErrorCode, "
                        + "substituta da DV8). Corpo: %s", corpo)
                .isEqualTo("PERSISTENCE_FAILURE");

        String mensagem = ler(corpo, "$.message");
        assertThat(mensagem)
                .as("a mensagem do ramo 500 é fixa e existe; vazia ou ausente não orienta "
                        + "ninguém. Corpo: %s", corpo)
                .isNotBlank();
        assertThat(mensagem)
                .as("a rede só afirma a causa que pode provar: num 23514 não há unicidade "
                        + "nenhuma — 'unicidade' na mensagem é a causa falsa que a ADR 0018 "
                        + "derruba. Corpo: %s", corpo)
                .doesNotContain("unicidade");
        assertThat(mensagem)
                .as("nada do banco na mensagem: texto de violação do Postgres carrega nome de "
                        + "tabela, coluna e constraint; a causa vai para o log, com stack, e "
                        + "nada dela para o corpo. Corpo: %s", corpo)
                .doesNotContain(plantacao, "addresses", "user_id", "constraint", "violates",
                        "ERROR", "23514");
        assertThat(corpo)
                .as("nem o nome da constraint plantada, nem o SQLSTATE, nem o driver, em lugar "
                        + "NENHUM da resposta (no corpo inteiro não se veta 'addresses' porque "
                        + "o path da rota a carrega)")
                .doesNotContain(plantacao, "23514", "PSQLException", "SQLState");

        assertThat(enderecosAposRecusa)
                .as("o INSERT recusado não gravou: só o endereço do setup existe")
                .isEqualTo(1);

        assertThat(gemeoInLoco.getResponse().getStatus())
                .as("gêmeo in-loco — desfeita a plantação, o MESMO POST do MESMO user volta ao "
                        + "caminho feliz: o desfecho de cima veio dela, não de outra coisa. "
                        + "Corpo: %s", corpoDe(gemeoInLoco))
                .isEqualTo(201);
    }

    













    @Test
    void desfeitaAPlantacaoACriacaoDeEnderecoVoltaAoCaminhoFeliz() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        String plantacao = "qa_dv10_" + UUID.randomUUID().toString().substring(0, 8);

        plantarCheckQueRecusaEnderecoNovoDe(cliente, plantacao);
        desfazerPlantacao(plantacao);

        MvcResult resposta = mockMvc.perform(novoEndereco(cliente)).andReturn();
        assertThat(resposta.getResponse().getStatus())
                .as("plantação desfeita = rota intacta: 201. Qualquer outra coisa aqui diz que "
                        + "o ciclo plantar→desfazer deixa rastro — e aí o caso 1 estaria "
                        + "passando (quando passar) por resíduo, não por produto. Corpo: %s",
                        corpoDe(resposta))
                .isEqualTo(201);
        assertThat(enderecosDe(cliente))
                .as("o do setup + o deste POST")
                .isEqualTo(2);
    }

    




























    @Test
    void corridaGenuinaDeEmailAlcancandoARedeContinua409UniquenessViolation() throws Exception {
        String email = "dv10-corrida-" + UUID.randomUUID().toString().substring(0, 8)
                + "@teste.local";

        mockMvc.perform(registrar("Primeira", email)).andExpect(status().isCreated());
        MvcResult segunda = mockMvc.perform(registrar("Segunda", email)).andReturn();
        String corpo = corpoDe(segunda);

        assertThat(segunda.getResponse().getStatus())
                .as("a corrida legítima de e-mail é a razão de ser do backstop: 409, conflito "
                        + "real de unicidade. 500 aqui significa que a rede passou a tratar o "
                        + "23505 como defeito nosso — a implementação barata que este controle "
                        + "existe para barrar. Corpo: %s", corpo)
                .isEqualTo(409);
        assertThat(TraducaoDaViolacaoDeIntegridadeTest.<String>ler(corpo, "$.code"))
                .as("UNIQUENESS_VIOLATION = o handler respondeu; EMAIL_ALREADY_REGISTERED = a "
                        + "pré-checagem respondeu e o INSERT nunca aconteceu — o andaime da "
                        + "cegueira parou de funcionar e este teste deixou de exercitar a rede. "
                        + "Corpo: %s", corpo)
                .isEqualTo("UNIQUENESS_VIOLATION");
        assertThat(TraducaoDaViolacaoDeIntegridadeTest.<String>ler(corpo, "$.message"))
                .as("'a mesma mensagem fixa de hoje — nada muda' (ADR 0018 §2.1): o literal "
                        + "pinado é o que acusa a vírgula que ninguém decidiu. Corpo: %s", corpo)
                .isEqualTo("operação viola uma restrição de unicidade");

        assertThat(contasCom(email))
                .as("o INSERT recusado não deixou segunda conta: a transação do segundo "
                        + "registro (user + cart) voltou inteira")
                .isEqualTo(1);
    }

    

    


























































































    @Test
    void corridaGenuinaDeEmailDeixaONomeDoIndiceNoLogDoServidorMasNaoNoCorpo() throws Exception {
        String email = "dv5b-log-" + UUID.randomUUID().toString().substring(0, 8)
                + "@teste.local";

        ch.qos.logback.classic.Logger raiz = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> captura = new ListAppender<>();
        captura.start();
        raiz.addAppender(captura);

        String corpo;
        int status;
        try {
            mockMvc.perform(registrar("Primeira", email)).andExpect(status().isCreated());
            MvcResult segunda = mockMvc.perform(registrar("Segunda", email)).andReturn();
            corpo = corpoDe(segunda);
            status = segunda.getResponse().getStatus();
        } finally {
            raiz.detachAppender(captura);
            captura.stop();
        }

        
        
        
        assertThat(status)
                .as("tem que ser o 23505 genuíno alcançando o handler (409), não a "
                        + "pré-checagem; é a condição de a captura ler o portador certo. "
                        + "Corpo: %s", corpo)
                .isEqualTo(409);
        assertThat(TraducaoDaViolacaoDeIntegridadeTest.<String>ler(corpo, "$.code"))
                .as("UNIQUENESS_VIOLATION = o handler respondeu ao INSERT recusado; "
                        + "EMAIL_ALREADY_REGISTERED = a pré-checagem respondeu e o INSERT "
                        + "nunca aconteceu (andaime da cegueira morto). Corpo: %s", corpo)
                .isEqualTo("UNIQUENESS_VIOLATION");

        String logDoRun = textoLogado(captura);

        assertThat(logDoRun)
                .as("nome do índice ausente no log capturado: o 23505 genuíno subiu pela "
                        + "camada JDBC/Hibernate e nada no log do run nomeou 'uk_users_email'. "
                        + "É a saída (iii) da ADR 0020 (o nome mora no log) quebrada — ou o "
                        + "portador de framework foi silenciado (config/upgrade), e aí a "
                        + "contingência do §3 item 4 (linha de log nossa no ramo 23505) vira "
                        + "par QA ≺ Dev. NÃO pino formato (HHH000247, layout): só a presença "
                        + "do nome. Log capturado: %s", logDoRun)
                .contains("uk_users_email");

        assertThat(corpo)
                .as("a metade 'contrato não se move' (ADR 0020 §3 item 1): o nome do índice "
                        + "é endereço interno de esquema e NÃO entra no corpo da resposta — "
                        + "nem agora nem como 'só o nome'. Corpo: %s", corpo)
                .doesNotContain("uk_users_email");
    }

    

    private record Cliente(String token, UUID addressId, UUID userId) {}

    private RequestBuilder checkout(Cliente cliente) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + cliente.addressId() + "\"}");
    }

    private int pedidosDe(Cliente cliente) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, cliente.userId());
        return total == null ? 0 : total;
    }

    private int abertosDe(Cliente cliente) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ? AND status = 'AWAITING_PAYMENT'",
                Integer.class, cliente.userId());
        return total == null ? 0 : total;
    }

    private static String corpoDe(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString();
    }

    







    private static String textoLogado(ListAppender<ILoggingEvent> captura) {
        StringBuilder texto = new StringBuilder();
        for (ILoggingEvent evento : captura.list) {
            texto.append(evento.getFormattedMessage()).append('\n');
            int profundidade = 0;
            for (IThrowableProxy p = evento.getThrowableProxy(); p != null && profundidade < 10;
                    p = p.getCause(), profundidade++) {
                texto.append(p.getClassName()).append(": ").append(p.getMessage()).append('\n');
            }
        }
        return texto.toString();
    }

    

    



    private void plantarCheckQueRecusaEnderecoNovoDe(Cliente cliente, String nome) {
        jdbc.execute("ALTER TABLE addresses ADD CONSTRAINT " + nome
                + " CHECK (user_id <> '" + cliente.userId() + "') NOT VALID");
    }

    

    private void desfazerPlantacao(String nome) {
        jdbc.execute("ALTER TABLE addresses DROP CONSTRAINT IF EXISTS " + nome);
    }

    

    private RequestBuilder novoEndereco(Cliente cliente) {
        return post("/me/addresses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"zipCode":"01310100","street":"Rua Dois","number":"200",
                         "complement":null,"district":"Centro","city":"Cidade","uf":"%s"}"""
                        .formatted(UfBrasileira.SP.name()));
    }

    private int enderecosDe(Cliente cliente) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM addresses WHERE user_id = ?", Integer.class, cliente.userId());
        return total == null ? 0 : total;
    }

    


    private RequestBuilder registrar(String nome, String email) {
        return post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Cliente %s","email":"%s","password":"senha-forte-123",
                         "phone":"11987654321"}""".formatted(nome, email));
    }

    private int contasCom(String email) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM users WHERE email = ?", Integer.class, email);
        return total == null ? 0 : total;
    }

    




    private static <T> T ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

    


    private Cliente novoClienteComEndereco() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "traducao-" + m + "@teste.local";

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
        String token = ler(login, "$.accessToken");

        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"Rua Um","number":"100",
                                 "complement":null,"district":"Centro","city":"Cidade","uf":"%s"}"""
                                .formatted(UfBrasileira.SP.name())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        return new Cliente(token, UUID.fromString(ler(endereco, "$.id")), userId);
    }

    

    private UUID criarPeca(String preco) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-traducao-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-traducao-" + m, CategorySizeType.ALPHA));
        Product produto = productRepository.save(Product.create(dona, categoria, "Peça " + m,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal(preco)));
        return produto.getId();
    }

    private void adicionarAoCarrinho(Cliente cliente, UUID productId) throws Exception {
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isOk());
    }
}
