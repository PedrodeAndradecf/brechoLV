package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Address;
import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Order;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.ShippingSnapshot;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.exception.PaymentGatewayException;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.OrderRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.PaymentGateway;
import Backend.BrechoAPI.service.ShippingService;
import Backend.BrechoAPI.service.impl.FakePixPaymentGateway;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

























































































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PedidoSemReprecoTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    

    
    private static final List<String> PALAVRAS_DE_VALOR = List.of(
            "subtotal", "shipping", "total", "price", "preco", "frete", "valor", "amount", "cost");

    

    private static final List<String> CAMPOS_DE_VALOR = List.of("subtotal", "shippingCost");

    private static final BigDecimal SUBTOTAL_ORIGINAL = new BigDecimal("100.00");
    private static final BigDecimal FRETE_ORIGINAL = new BigDecimal("19.90");
    private static final Instant PRAZO = Instant.parse("2026-10-07T15:30:00Z");

    



    @Test
    void orderNaoExpoeEscritorDeValor() {
        assertThat(escritoresPorAssinatura(Order.class))
                .as("DV0-b / ADR 0015 D-4 — REPREÇO É CANCELAMENTO + PEDIDO NOVO, NUNCA UPDATE. "
                        + "Estes métodos de Order são caminho de escrita de valor depois de criado "
                        + "(set*, parâmetro BigDecimal, ou nome de valor que não é leitor). A PV2 "
                        + "§5 caso B1 mediu: o mesmo pedido com valor diferente gera SEGUNDA "
                        + "cobrança viva no fornecedor")
                .isEmpty();

        List<String> escritaPeloRepositorio = new ArrayList<>();
        for (Method m : OrderRepository.class.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Modifying.class) || m.isAnnotationPresent(Query.class)) {
                escritaPeloRepositorio.add(m.getName());
            }
        }
        assertThat(escritaPeloRepositorio)
                .as("OrderRepository não tem @Modifying/@Query: um 'update Order set subtotal' "
                        + "por JPQL contornaria a entidade inteira. Se um destes for legítimo "
                        + "(leitura), o analisador precisa ficar mais fino — não apagado")
                .isEmpty();
    }

    




    @Test
    void nenhumMetodoDeOrderMudaOValorDeUmPedidoCriado() throws Exception {
        Order pedido = Order.create(cliente(), snapshot(), SUBTOTAL_ORIGINAL, FRETE_ORIGINAL, PRAZO);

        Resultado r = escritoresPorChamada(Order.class, pedido);

        assertThat(r.invocados())
                .as("guarda de vacuidade: o analisador tem de ter EXERCITADO os métodos que "
                        + "existem — zero aqui é um 'nenhum mudou' que nunca chamou nada")
                .contains("expire", "total", "isDueAt", "getSubtotal", "getShippingCost");
        assertThat(r.escritores())
                .as("DV0-b / ADR 0015 D-4 — estes métodos MUDARAM o valor de um pedido já "
                        + "criado (campo antes → depois). Repreço é cancelamento + pedido novo, "
                        + "nunca update: a PV2 §5 caso B1 mediu segunda cobrança viva")
                .isEmpty();
        assertThat(pedido.total())
                .as("e o total do pedido, depois de tudo, é o do nascimento")
                .isEqualByComparingTo("119.90");
    }

    






    @Test
    void controleOsAnalisadoresAcusamUmSetTotalPlantado() throws Exception {
        assertThat(escritoresPorAssinatura(OrderComSetTotalPlantado.class))
                .as("controle do analisador 1: um setTotal(BigDecimal) TEM de ser acusado")
                .anySatisfy(v -> assertThat(v).contains("setTotal"));

        OrderComSetTotalPlantado plantado = new OrderComSetTotalPlantado();
        definirCampo(plantado, "subtotal", SUBTOTAL_ORIGINAL);
        definirCampo(plantado, "shippingCost", FRETE_ORIGINAL);

        assertThat(escritoresPorChamada(OrderComSetTotalPlantado.class, plantado).escritores())
                .as("controle do analisador 2: o setTotal que reescreve subtotal TEM de ser "
                        + "acusado pela leitura do campo")
                .anySatisfy(v -> assertThat(v).contains("setTotal"));
    }

    
    static class OrderComSetTotalPlantado extends Order {
        public void setTotal(BigDecimal total) {
            try {
                BigDecimal frete = (BigDecimal) campo("shippingCost").get(this);
                campo("subtotal").set(this, total.subtract(frete));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    

    @TestConfiguration
    static class PortaDesteTeste {
        @Bean
        @Primary
        PortaQueRegistraAmount portaQueRegistraAmount() {
            return new PortaQueRegistraAmount();
        }
    }

    





    static final class PortaQueRegistraAmount implements PaymentGateway {
        record Chamada(UUID orderId, BigDecimal amount) {}

        private final PaymentGateway real = new FakePixPaymentGateway();
        private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
        private volatile boolean falhar;

        @Override
        public PixCharge createPixCharge(UUID orderId, BigDecimal amount, Instant expiresAt) {
            chamadas.add(new Chamada(orderId, amount));
            if (falhar) {
                throw new PaymentGatewayException(
                        "porta deste teste fora do ar (DV0-b): a cliente vai voltar", null);
            }
            return real.createPixCharge(orderId, amount, expiresAt);
        }

        List<BigDecimal> amountsDe(UUID orderId) {
            return chamadas.stream().filter(c -> c.orderId().equals(orderId))
                    .map(Chamada::amount).toList();
        }

        

        Map<UUID, TreeSet<BigDecimal>> pedidosReprecados() {
            Map<UUID, TreeSet<BigDecimal>> porPedido = new LinkedHashMap<>();
            for (Chamada c : chamadas) {
                porPedido.computeIfAbsent(c.orderId(), k -> new TreeSet<>()).add(c.amount());
            }
            porPedido.values().removeIf(amounts -> amounts.size() < 2);
            return porPedido;
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PortaQueRegistraAmount porta;
    @Autowired PaymentGateway portaInjetadaNoSistema;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    

    @MockitoSpyBean ShippingService shippingService;

    @BeforeEach
    void portaLimpaENoAr() {
        porta.chamadas.clear();
        porta.falhar = false;
    }

    



    @Test
    void retomadaDepoisDeReajusteDePrecoNaoCobraOutroAmount() throws Exception {
        assertThat(portaInjetadaNoSistema)
                .as("a porta que o CheckoutService recebe é a deste teste — senão nada é registrado")
                .isSameAs(porta);

        Cliente cliente = novoClienteComEndereco();
        Peca peca = criarPeca("100.00");
        adicionarAoCarrinho(cliente.token(), peca.productId());
        BigDecimal frete = shippingService.costFor(UfBrasileira.SP);

        porta.falhar = true;
        mockMvc.perform(checkout(cliente)).andExpect(status().isBadGateway());
        UUID orderId = oUnicoPedidoDe(cliente);

        
        editarPreco(peca, "500.00");
        mockMvc.perform(get("/products/{id}", peca.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(500.00));   

        porta.falhar = false;
        mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));

        assertQueAPortaFoiChamadaDuasVezesComOMesmoAmount(orderId,
                SUBTOTAL_ORIGINAL.add(frete), "o preço da peça foi de 100.00 para 500.00");
    }

    



    @Test
    void retomadaDepoisDeReajusteDeFreteNaoCobraOutroAmount() throws Exception {
        Cliente cliente = novoClienteComEndereco();
        Peca peca = criarPeca("100.00");
        adicionarAoCarrinho(cliente.token(), peca.productId());
        BigDecimal frete = shippingService.costFor(UfBrasileira.SP);

        porta.falhar = true;
        mockMvc.perform(checkout(cliente)).andExpect(status().isBadGateway());
        UUID orderId = oUnicoPedidoDe(cliente);

        BigDecimal reajustado = frete.add(new BigDecimal("80.00"));
        doReturn(reajustado).when(shippingService).costFor(UfBrasileira.SP);
        assertThat(shippingService.costFor(UfBrasileira.SP))
                .as("a mudança é REAL: a tabela agora diz outro frete para SP")
                .isEqualByComparingTo(reajustado)
                .isNotEqualByComparingTo(frete);

        porta.falhar = false;
        mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()));

        assertQueAPortaFoiChamadaDuasVezesComOMesmoAmount(orderId,
                SUBTOTAL_ORIGINAL.add(frete), "o frete de SP foi de " + frete + " para " + reajustado);
    }

    private void assertQueAPortaFoiChamadaDuasVezesComOMesmoAmount(
            UUID orderId, BigDecimal esperado, String oQueMudou) {
        assertThat(porta.amountsDe(orderId))
                .as("guarda de vacuidade: a porta tem de ter sido chamada DUAS vezes para este "
                        + "pedido (a que falhou e a da retomada). Uma só, e 'nunca dois amounts' "
                        + "é verdade por falta de segunda chamada")
                .hasSize(2);
        assertThat(porta.pedidosReprecados())
                .as("DV0-b, asserção 2 — NUNCA uma segunda chamada à porta com amount DIFERENTE "
                        + "para o mesmo orderId (" + oQueMudou + " entre as duas). No fornecedor "
                        + "real isto é a PV2 §5 caso B1: segunda URL, diferente e igualmente "
                        + "válida — duas cobranças vivas para um pedido. Repreço é cancelamento "
                        + "+ pedido novo (ADR 0015 D-4)")
                .isEmpty();
        assertThat(porta.amountsDe(orderId))
                .as("e o amount é o CONGELADO no nascimento (100.00 + frete de então), não o "
                        + "do mundo de agora")
                .allSatisfy(a -> assertThat(a).isEqualByComparingTo(esperado));
        BigDecimal naLinha = jdbc.queryForObject(
                "SELECT subtotal + shipping_cost FROM orders WHERE id = ?", BigDecimal.class, orderId);
        assertThat(naLinha)
                .as("e é o que a linha de orders diz: a porta cobra o que o pedido guarda")
                .isEqualByComparingTo(esperado);
    }

    

    private static List<String> escritoresPorAssinatura(Class<? extends Order> tipo) {
        List<String> violacoes = new ArrayList<>();
        for (Method m : metodosAte(tipo)) {
            if (Modifier.isPrivate(m.getModifiers()) || m.isSynthetic() || m.isBridge()) continue;
            String nome = m.getName();
            boolean deInstancia = !Modifier.isStatic(m.getModifiers());
            if (nome.startsWith("set")) {
                violacoes.add(assinatura(m) + " — setter");
            } else if (deInstancia && List.of(m.getParameterTypes()).contains(BigDecimal.class)) {
                violacoes.add(assinatura(m) + " — recebe dinheiro depois de criado");
            } else if (deInstancia && falaDeValor(nome)
                    && (m.getParameterCount() > 0 || m.getReturnType() == void.class)) {
                violacoes.add(assinatura(m) + " — nome de valor e não é leitor");
            }
        }
        return violacoes;
    }

    private record Resultado(List<String> invocados, List<String> escritores) {}

    private static Resultado escritoresPorChamada(Class<? extends Order> tipo, Order alvo)
            throws Exception {
        List<String> invocados = new ArrayList<>();
        List<String> escritores = new ArrayList<>();
        for (Method m : metodosAte(tipo)) {
            int mod = m.getModifiers();
            if (Modifier.isPrivate(mod) || Modifier.isStatic(mod) || m.isSynthetic() || m.isBridge()) {
                continue;
            }
            List<Object> antes = valoresDe(alvo);
            m.setAccessible(true);
            try {
                m.invoke(alvo, argumentosSinteticos(m));
            } catch (InvocationTargetException recusou) {
                
            }
            invocados.add(m.getName());
            List<Object> depois = valoresDe(alvo);
            for (int i = 0; i < CAMPOS_DE_VALOR.size(); i++) {
                if (!mesmoValor(antes.get(i), depois.get(i))) {
                    escritores.add(assinatura(m) + " — " + CAMPOS_DE_VALOR.get(i) + ": "
                            + antes.get(i) + " → " + depois.get(i));
                }
            }
            if (!escritores.isEmpty()) {
                
                definirCampo(alvo, "subtotal", antes.get(0));
                definirCampo(alvo, "shippingCost", antes.get(1));
            }
        }
        return new Resultado(invocados, escritores);
    }

    private static List<Method> metodosAte(Class<?> tipo) {
        List<Method> todos = new ArrayList<>();
        for (Class<?> c = tipo; c != null && Order.class.isAssignableFrom(c); c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().startsWith("$$_hibernate")) todos.add(m);
            }
        }
        return todos;
    }

    private static boolean falaDeValor(String nome) {
        String n = nome.toLowerCase(Locale.ROOT);
        return PALAVRAS_DE_VALOR.stream().anyMatch(n::contains);
    }

    private static Object[] argumentosSinteticos(Method m) {
        Class<?>[] tipos = m.getParameterTypes();
        Object[] args = new Object[tipos.length];
        for (int i = 0; i < tipos.length; i++) {
            Class<?> t = tipos[i];
            if (t == BigDecimal.class) args[i] = new BigDecimal("12345.67");
            else if (t == Instant.class) args[i] = PRAZO.plusSeconds(3600);
            else if (t == String.class) args[i] = "x";
            else if (t == boolean.class) args[i] = true;
            else if (t == int.class) args[i] = 7;
            else if (t == long.class) args[i] = 7L;
            else if (t.isPrimitive()) args[i] = null; 
            else args[i] = null;
        }
        return args;
    }

    private static List<Object> valoresDe(Order pedido) throws IllegalAccessException {
        List<Object> valores = new ArrayList<>();
        for (String nome : CAMPOS_DE_VALOR) {
            valores.add(campo(nome).get(pedido));
        }
        return valores;
    }

    private static boolean mesmoValor(Object a, Object b) {
        if (a == null || b == null) return a == b;
        return ((BigDecimal) a).compareTo((BigDecimal) b) == 0;
    }

    private static String assinatura(Method m) {
        StringBuilder s = new StringBuilder(m.getDeclaringClass().getSimpleName())
                .append('.').append(m.getName()).append('(');
        Class<?>[] p = m.getParameterTypes();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) s.append(", ");
            s.append(p[i].getSimpleName());
        }
        return s.append(')').toString();
    }

    private static Field campo(String nome) {
        try {
            Field f = Order.class.getDeclaredField(nome);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Order perdeu o campo " + nome
                    + " — o analisador precisa acompanhar o renome, não ser apagado", e);
        }
    }

    private static void definirCampo(Order alvo, String nome, Object valor)
            throws IllegalAccessException {
        campo(nome).set(alvo, valor);
    }

    private static User cliente() {
        return User.createClient("Cliente DV0-b", "dv0b@teste.local", "hash", "11987654321");
    }

    private static ShippingSnapshot snapshot() {
        User c = cliente();
        Address a = Address.create(c, "01310100", "Rua Um", "100", null, "Centro",
                "São Paulo", UfBrasileira.SP);
        return ShippingSnapshot.copyOf(a, c);
    }

    

    private record Cliente(String token, UUID addressId, UUID userId) {}

    private record Peca(UUID productId, UUID categoryId, String titulo, CustomUserDetails dona) {}

    private RequestBuilder checkout(Cliente cliente) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + cliente.addressId() + "\"}");
    }

    private UUID oUnicoPedidoDe(Cliente cliente) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM orders WHERE user_id = ?",
                UUID.class, cliente.userId());
        assertThat(ids).as("a falha da porta não apaga o pedido (T12)").hasSize(1);
        return ids.get(0);
    }

    private Cliente novoClienteComEndereco() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String email = "dv0b-" + m + "@teste.local";
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
        String token = JsonPath.read(login, "$.accessToken");
        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"Rua Um","number":"100",
                                 "complement":null,"district":"Centro","city":"Cidade",
                                 "uf":"SP"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);
        return new Cliente(token, UUID.fromString(JsonPath.read(endereco, "$.id")), userId);
    }

    private Peca criarPeca(String preco) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-dv0b-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-dv0b-" + m, CategorySizeType.ALPHA));
        String titulo = "Peça DV0-b " + m;
        Product produto = productRepository.save(Product.create(dona, categoria, titulo,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal(preco)));
        return new Peca(produto.getId(), categoria.getId(), titulo, new CustomUserDetails(dona));
    }

    private void adicionarAoCarrinho(String token, UUID productId) throws Exception {
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isOk());
    }

    

    private void editarPreco(Peca peca, String preco) throws Exception {
        mockMvc.perform(put("/seller/products/{id}", peca.productId())
                        .with(user(peca.dona()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"desc","brand":"Marca","color":"azul",
                                 "size":"M","sizeLabel":null,"condition":"GOOD","price":%s,
                                 "categoryId":"%s"}""".formatted(peca.titulo(), preco, peca.categoryId())))
                .andExpect(status().isOk());
    }

    










































































































































}
