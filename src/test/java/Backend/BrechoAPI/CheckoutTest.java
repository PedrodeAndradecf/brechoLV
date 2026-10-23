package Backend.BrechoAPI;

import Backend.BrechoAPI.config.ShippingProperties;
import Backend.BrechoAPI.domain.Address;
import Backend.BrechoAPI.domain.Cart;
import Backend.BrechoAPI.domain.CartItem;
import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.CategorySizeType;
import Backend.BrechoAPI.domain.enums.ProductCondition;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.repository.AddressRepository;
import Backend.BrechoAPI.repository.CartItemRepository;
import Backend.BrechoAPI.repository.CartRepository;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import Backend.BrechoAPI.security.CustomUserDetails;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;






















































@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CheckoutTest {

    
































































































































































































































































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

    
    private static final UfBrasileira UF_SEM_FRETE = UfBrasileira.AC;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    @Autowired ShippingService shippingService;
    @Autowired UserRepository userRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired ProductRepository productRepository;
    @Autowired AddressRepository addressRepository;
    @Autowired CartRepository cartRepository;
    @Autowired CartItemRepository cartItemRepository;

    

    














    @Test
    void aTabelaDeFreteDesteTesteEstaMesmoNoAr() {
        assertThat(shippingService.costFor(UfBrasileira.SP))
                .as("o bean @Primary do teste diz 19.90 para SP; se vier o valor de "
                        + "application.properties, a substituição não aconteceu")
                .isEqualByComparingTo("19.90");

        assertThat(catchThrowable(() -> shippingService.costFor(UF_SEM_FRETE)))
                .as("AC está nas 27 UFs de application.properties e FORA da tabela deste "
                        + "teste — se não recusar aqui, o teste 6 não exercita UF sem frete")
                .isInstanceOf(DomainRuleException.class);
    }

    

    

























    @Test
    void checkoutDevolveCodigoPixCopiavel() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());
        adicionarAoCarrinho(cliente.token(), criarPeca("130.00").productId());

        MvcResult resposta = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.pix.txid").isNotEmpty())
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty())
                .andReturn();

        String corpo = resposta.getResponse().getContentAsString();

        assertThat(CheckoutTest.<String>ler(corpo, "$.pix.copyPaste"))
                .as("um código Pix em branco satisfaz 'existe' e não serve para pagar nada")
                .isNotBlank();
        assertThat(CheckoutTest.<String>ler(corpo, "$.pix.txid"))
                .as("sem txid a cobrança não tem referência, e nada no gateway pode ser "
                        + "reencontrado depois")
                .isNotBlank();

        
        assertThat(corpo)
                .as("subtotal com escala 2 e igual à SOMA das duas peças — 'preço do "
                        + "primeiro item' daria 70.00 e 'preço × itens' daria 140.00")
                .contains("\"subtotal\":200.00");
        assertThat(corpo)
                .as("o frete vem da tabela deste teste (SP = 19.90), com centavo")
                .contains("\"shippingCost\":19.90");
        assertThat(corpo)
                .as("total é subtotal + frete (decisão 5: soma, não coluna). Um total() que "
                        + "devolvesse só o subtotal daria 200.00 e passaria em tudo o mais")
                .contains("\"total\":219.90");

        
        UUID orderId = UUID.fromString(ler(corpo, "$.id"));
        String guardado = jdbc.queryForObject(
                "SELECT pix_copy_paste FROM payments WHERE order_id = ?", String.class, orderId);

        assertThat(guardado)
                .as("o código entregue tem que ser o código guardado: se a resposta o gera e "
                        + "não o persiste, ele existe por uma resposta HTTP só e some quando o "
                        + "cliente fecha a tela")
                .isEqualTo(ler(corpo, "$.pix.copyPaste"));

        String txidGuardado = jdbc.queryForObject(
                "SELECT txid FROM payments WHERE order_id = ?", String.class, orderId);
        assertThat(txidGuardado).isEqualTo(ler(corpo, "$.pix.txid"));

        
        assertThat(jdbc.queryForObject(
                "SELECT subtotal::text || '|' || shipping_cost::text FROM orders WHERE id = ?",
                String.class, orderId))
                .as("as duas parcelas congeladas na linha do pedido, em centavo")
                .isEqualTo("200.00|19.90");
    }

    












    @Test
    void statusVemDoConjuntoFechado() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        MvcResult resposta = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_PAYMENT"))
                .andReturn();

        
        
        UUID orderId = idDoPedido(resposta);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId))
                .as("o status da resposta e o da coluna não podem divergir num pedido recém-criado")
                .isEqualTo("AWAITING_PAYMENT");
    }

    












    @Test
    void expiresAtEhInstanteISO8601EmUTC() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        Instant agora = clock.instant();
        MvcResult resposta = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();

        Object prazo = ler(resposta.getResponse().getContentAsString(), "$.expiresAt");

        assertThat(prazo)
                .as("epoch millis é JSON válido e quebra todo consumidor em silêncio — "
                        + "expiresAt precisa ser texto, no mesmo formato que ApiError.timestamp")
                .isInstanceOf(String.class);
        assertThat((String) prazo)
                .as("ISO-8601 em UTC, com o Z no fim: '2026-09-05T19:00:00Z'")
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        assertThat(Instant.parse((String) prazo))
                .as("prazo no passado nasce vencido: o cliente recebe um código Pix que "
                        + "nenhum caminho aceita pagar")
                .isAfter(agora);
    }

    




















    @Test
    void checkoutGravaOSnapshotDoEnderecoEscolhido() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.RJ);   
        UUID enderecoEscolhido = criarEndereco(cliente.token(), UfBrasileira.SP,
                "Rua da Entrega Certa", "742", "Vila Madalena", "São Paulo");
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        MvcResult resposta = mockMvc.perform(checkout(cliente, enderecoEscolhido))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipping.street").value("Rua da Entrega Certa"))
                .andExpect(jsonPath("$.shipping.number").value("742"))
                .andExpect(jsonPath("$.shipping.city").value("São Paulo"))
                .andExpect(jsonPath("$.shipping.uf").value("SP"))
                .andExpect(jsonPath("$.shipping.recipientName").value(cliente.nome()))
                .andExpect(jsonPath("$.shipping.phone").value(TELEFONE_DO_CLIENTE))
                .andReturn();

        UUID orderId = idDoPedido(resposta);

        assertThat(jdbc.queryForObject(
                "SELECT ship_street || '|' || ship_number || '|' || ship_city || '|' || ship_uf "
                        + "|| '|' || ship_recipient_name || '|' || ship_phone FROM orders WHERE id = ?",
                String.class, orderId))
                .as("o pedido despacha pelo endereço que a cliente escolheu no corpo do POST, "
                        + "não pelo primeiro que ela tem cadastrado — o outro endereço deste "
                        + "cliente é no RJ, e um frete de 24.90 no lugar de 19.90 seria o "
                        + "sintoma barato do mesmo bug")
                .isEqualTo("Rua da Entrega Certa|742|São Paulo|SP|" + cliente.nome()
                        + "|" + TELEFONE_DO_CLIENTE);

        
        
        assertThat(jdbc.queryForObject("SELECT shipping_cost::text FROM orders WHERE id = ?",
                String.class, orderId))
                .isEqualTo("19.90");
    }

    




































































































    @Test
    void osNoveCamposDoSnapshotVoltamNoPost() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.RJ);   
        String endereco = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"04538133","street":"Alameda dos Nove Campos",
                                 "number":"1071","complement":"Torre B, sala 93",
                                 "district":"Jardim Congelado","city":"Barueri","uf":"SP"}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID enderecoEscolhido = UUID.fromString(ler(endereco, "$.id"));
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        mockMvc.perform(checkout(cliente, enderecoEscolhido))
                .andExpect(status().isCreated())
                
                
                
                .andExpect(jsonPath("$.shipping.recipientName").value(cliente.nome()))
                .andExpect(jsonPath("$.shipping.phone").value(TELEFONE_DO_CLIENTE))
                .andExpect(jsonPath("$.shipping.zipCode").value("04538133"))
                .andExpect(jsonPath("$.shipping.street").value("Alameda dos Nove Campos"))
                .andExpect(jsonPath("$.shipping.number").value("1071"))
                .andExpect(jsonPath("$.shipping.complement").value("Torre B, sala 93"))
                .andExpect(jsonPath("$.shipping.district").value("Jardim Congelado"))
                .andExpect(jsonPath("$.shipping.city").value("Barueri"))
                .andExpect(jsonPath("$.shipping.uf").value("SP"));
    }

    

    











    @Test
    void checkoutNaoEsvaziaOCarrinho() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        UUID peca = criarPeca("70.00").productId();
        adicionarAoCarrinho(cliente.token(), peca);

        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        mockMvc.perform(get("/me/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(peca.toString()));
    }

    





















    @Test
    void pecaContinuaDisponivelDepoisDoCheckout() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        UUID peca = criarPeca("70.00").productId();
        adicionarAoCarrinho(cliente.token(), peca);

        String antes = jdbc.queryForObject(
                "SELECT updated_at::text || '|' || version::text || '|' || coalesce(deleted_at::text, 'null') "
                        + "FROM products WHERE id = ?", String.class, peca);

        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        Cliente outra = novoClienteComEndereco(UfBrasileira.SP);
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + outra.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + peca + "\"}"))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT updated_at::text || '|' || version::text || '|' || coalesce(deleted_at::text, 'null') "
                        + "FROM products WHERE id = ?", String.class, peca))
                .as("o checkout não escreve em products: sem status, sem reserved_until, "
                        + "sem UPDATE nenhum — a disponibilidade continua sendo deleted_at IS NULL")
                .isEqualTo(antes);
    }

    

    










    @Test
    void checkoutDeCarrinhoVazioEhRecusado() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CART_EMPTY"));

        assertThat(pedidosDe(cliente)).isZero();
    }

    








    @Test
    void checkoutComEnderecoDeOutroClienteEh404() throws Exception {
        Cliente dona = novoClienteComEndereco(UfBrasileira.SP);
        Cliente intrusa = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(intrusa.token(), criarPeca("70.00").productId());

        mockMvc.perform(checkout(intrusa, dona.addressId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));

        assertThat(pedidosDe(intrusa))
                .as("recusa antes de escrever: nada de pedido para quem apontou endereço alheio")
                .isZero();
    }

    

















    @Test
    void checkoutParaUfSemFreteEhRecusado() throws Exception {
        Cliente cliente = novoClienteComEndereco(UF_SEM_FRETE);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHIPPING_UNAVAILABLE_FOR_UF"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        UF_SEM_FRETE.name())));

        assertThat(pedidosDe(cliente))
                .as("frete antes de tudo que escreve: falhar aqui é 422 e NADA foi gravado")
                .isZero();
    }

    

















    @Test
    void checkoutDePecaArquivadaDepoisDeEntrarNoCarrinho() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca arquivada = criarPeca("70.00");
        Peca intacta = criarPeca("130.00");
        adicionarAoCarrinho(cliente.token(), arquivada.productId());
        adicionarAoCarrinho(cliente.token(), intacta.productId());

        mockMvc.perform(delete("/seller/products/{id}", arquivada.productId())
                        .with(user(arquivada.dona())))
                .andExpect(status().isNoContent());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(arquivada.titulo())))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                intacta.titulo()))));

        assertThat(pedidosDe(cliente))
                .as("checagem A antes de escrever: recusa não deixa pedido pela metade")
                .isZero();
    }

    







































    @Test
    void contaSemTelefoneNaoFechaPedido() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User semTelefone = userRepository.save(User.createWithRole(
                "Vendedora " + m, "sem-telefone-" + m + "@teste.local", "hash", Role.SELLER));
        Address endereco = addressRepository.save(Address.create(semTelefone, "01310100",
                "Av. Paulista", "1000", null, "Bela Vista", "São Paulo", UfBrasileira.SP));

        
        
        Cart carrinho = cartRepository.save(Cart.create(semTelefone));
        cartItemRepository.save(CartItem.create(carrinho,
                productRepository.findById(criarPeca("70.00").productId()).orElseThrow()));

        mockMvc.perform(post("/me/orders")
                        .with(user(new CustomUserDetails(semTelefone)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + endereco.getId() + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOMAIN_RULE_VIOLATION"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?",
                Integer.class, semTelefone.getId()))
                .as("a guarda recusa ANTES de escrever — nenhum pedido meio montado")
                .isZero();
    }

    

    












    @Test
    void segundoCheckoutComOMesmoCarrinhoRetomaOPedido() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        MvcResult primeiro = mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn();
        UUID orderId = idDoPedido(primeiro);

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty());

        assertThat(pedidosDe(cliente))
                .as("retomada é o MESMO pedido: 200 com linha nova é pedido duplicado com "
                        + "cara de idempotência, e duas cobranças Pix vivas no gateway")
                .isEqualTo(1);
    }

    















    @Test
    void segundoCheckoutComCarrinhoDiferenteEhRecusado() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        adicionarAoCarrinho(cliente.token(), criarPeca("130.00").productId());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_OPEN"));

        assertThat(pedidosDe(cliente))
                .as("a recusa não pode ter criado o segundo pedido antes de recusar")
                .isEqualTo(1);
    }

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    





































































































































































    
















    @Test
    void enderecoAlheioComPedidoAbertoTambemEh404() throws Exception {
        Cliente dona = novoClienteComEndereco(UfBrasileira.SP);
        Cliente outra = novoClienteComEndereco(UfBrasileira.SP);
        UUID enderecoAlheio = criarEndereco(outra.token(), UfBrasileira.SP,
                "Rua da Outra Cliente", "999", "Bairro", "Cidade");
        adicionarAoCarrinho(dona.token(), criarPeca("70.00").productId());

        UUID orderId = idDoPedido(mockMvc.perform(checkout(dona))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(checkout(dona, enderecoAlheio))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"));

        assertThat(pedidosDe(dona))
                .as("a recusa não cria pedido novo e não apaga o aberto")
                .isEqualTo(1);
        assertThat(statusDoPedido(orderId))
                .as("o pedido aberto fica intacto: recusar não é cancelar")
                .isEqualTo("AWAITING_PAYMENT");
        assertThat(ruaDoPedido(orderId))
                .as("o endereço alheio não pode ter entrado no snapshot; se entrou, a rota "
                        + "aceitou um endereço que não é da cliente e o congelou")
                .isEqualTo("Rua Um");
    }

    












    @Test
    void enderecoInexistenteComPedidoAbertoTambemEh404() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Cliente outra = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        mockMvc.perform(checkout(cliente)).andExpect(status().isCreated());

        MvcResult inexistente = mockMvc.perform(checkout(cliente, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ADDRESS_NOT_FOUND"))
                .andReturn();

        MvcResult alheio = mockMvc.perform(checkout(cliente, outra.addressId()))
                .andExpect(status().isNotFound())
                .andReturn();

        String corpoInexistente = inexistente.getResponse().getContentAsString();
        String corpoAlheio = alheio.getResponse().getContentAsString();

        assertThat((String) ler(corpoAlheio, "$.code"))
                .as("o endereço que EXISTE e é de outra pessoa tem que sair pelo mesmo código")
                .isEqualTo(ler(corpoInexistente, "$.code"));
        assertThat((String) ler(corpoAlheio, "$.message"))
                .as("INDISTINGUÍVEL é o contrato: mensagem diferente para 'não existe' e "
                        + "'não é seu' transforma a rota num oráculo de quais endereços existem")
                .isEqualTo(ler(corpoInexistente, "$.message"));

        assertThat(pedidosDe(cliente)).isEqualTo(1);
    }

    














    @Test
    void retomadaComOMesmoEnderecoContinuaSendo200() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        criarEndereco(cliente.token(), UfBrasileira.SP,
                "Rua Dois da Retomada", "222", "Bairro", "Cidade");
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(checkout(cliente, cliente.addressId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty())
                .andExpect(jsonPath("$.shipping.street").value("Rua Um"));

        assertThat(pedidosDe(cliente))
                .as("retomar com o endereço certo continua sendo O MESMO pedido")
                .isEqualTo(1);
    }

    























    @Test
    void retomadaComEnderecoDiferenteDoCongeladoEhRecusada() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        UUID outroEnderecoDela = criarEndereco(cliente.token(), UfBrasileira.SP,
                "Rua Dois da Retomada", "222", "Bairro", "Cidade");
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(checkout(cliente, outroEnderecoDela))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_OPEN"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(orderId.toString())))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("Rua Um")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                "Rua Dois da Retomada"))));

        assertThat(pedidosDe(cliente))
                .as("recusar a retomada não pode criar o segundo pedido")
                .isEqualTo(1);
        assertThat(ruaDoPedido(orderId))
                .as("e não pode reescrever o snapshot do pedido aberto com o endereço pedido")
                .isEqualTo("Rua Um");
    }

    
























    @Test
    void retomadaDePedidoComPecaArquivadaEhRecusada() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca arquivada = criarPeca("70.00");
        Peca intacta = criarPeca("130.00");
        adicionarAoCarrinho(cliente.token(), arquivada.productId());
        adicionarAoCarrinho(cliente.token(), intacta.productId());

        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        
        mockMvc.perform(delete("/seller/products/{id}", arquivada.productId())
                        .with(user(arquivada.dona())))
                .andExpect(status().isNoContent());

        
        
        
        mockMvc.perform(get("/products/{id}", arquivada.productId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(arquivada.titulo())))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                intacta.titulo()))));

        assertThat(pedidosDe(cliente))
                .as("recusar a retomada não cria pedido novo")
                .isEqualTo(1);
        assertThat(statusDoPedido(orderId))
                .as("e não cancela o aberto: a recusa é sobre ENTREGAR o Pix de novo e "
                        + "AFIRMAR que o pedido está bem, não sobre desfazer o pedido "
                        + "(ADR 0003, 'o que esta decisão não compra')")
                .isEqualTo("AWAITING_PAYMENT");
    }

    
















    @Test
    void retomadaComTodasAsPecasDisponiveisContinuaSendo200() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca uma = criarPeca("70.00");
        Peca outra = criarPeca("130.00");
        adicionarAoCarrinho(cliente.token(), uma.productId());
        adicionarAoCarrinho(cliente.token(), outra.productId());

        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.subtotal").value(200.00))
                .andExpect(jsonPath("$.pix.copyPaste").isNotEmpty());

        assertThat(pedidosDe(cliente))
                .as("a checagem A na retomada não pode recusar peça que está à venda")
                .isEqualTo(1);
    }

    


































    @Test
    void mensagemDaRetomadaRecusadaNaoMandaRemoverDoCarrinho() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca arquivada = criarPeca("70.00");
        adicionarAoCarrinho(cliente.token(), arquivada.productId());

        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(delete("/seller/products/{id}", arquivada.productId())
                        .with(user(arquivada.dona())))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/products/{id}", arquivada.productId()))
                .andExpect(status().isNotFound());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(arquivada.titulo())))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(orderId.toString())))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsStringIgnoringCase("prazo")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsStringIgnoringCase("carrinho"))));
    }

    













    @Test
    void mensagemDaCriacaoContinuaMandandoRemoverDoCarrinho() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca arquivada = criarPeca("70.00");
        adicionarAoCarrinho(cliente.token(), arquivada.productId());

        mockMvc.perform(delete("/seller/products/{id}", arquivada.productId())
                        .with(user(arquivada.dona())))
                .andExpect(status().isNoContent());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsStringIgnoringCase("carrinho")));

        assertThat(pedidosDe(cliente))
                .as("sem pedido aberto a recusa é a de sempre: nada é criado")
                .isZero();
    }

    






















    @Test
    void tituloNaRecusaDaRetomadaVemDoPedidoCongelado() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca peca = criarPeca("70.00");
        String tituloCongelado = peca.titulo();
        String tituloNovo = "Peça RENOMEADA depois do pedido";
        adicionarAoCarrinho(cliente.token(), peca.productId());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].title").value(tituloCongelado));

        editarPeca(peca, "70.00", tituloNovo);

        
        mockMvc.perform(get("/products/{id}", peca.productId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(tituloNovo));

        mockMvc.perform(delete("/seller/products/{id}", peca.productId())
                        .with(user(peca.dona())))
                .andExpect(status().isNoContent());

        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_AVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(tituloCongelado)))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(tituloNovo))));
    }

    























































    




















































    @Test
    void conjuntoDiferenteEPecaArquivadaNoPedidoEhOrderAlreadyOpen() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        Peca arquivada = criarPeca("70.00");
        Peca intacta = criarPeca("130.00");
        adicionarAoCarrinho(cliente.token(), arquivada.productId());
        adicionarAoCarrinho(cliente.token(), intacta.productId());

        UUID orderId = idDoPedido(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andReturn());

        
        Peca aMais = criarPeca("45.00");
        adicionarAoCarrinho(cliente.token(), aMais.productId());

        
        
        
        
        
        mockMvc.perform(get("/me/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        
        mockMvc.perform(delete("/seller/products/{id}", arquivada.productId())
                        .with(user(arquivada.dona())))
                .andExpect(status().isNoContent());

        
        mockMvc.perform(get("/products/{id}", arquivada.productId()))
                .andExpect(status().isNotFound());

        
        mockMvc.perform(checkout(cliente))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_ALREADY_OPEN"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(orderId.toString())))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString(ruaDoPedido(orderId))))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                arquivada.titulo()))));

        assertThat(pedidosDe(cliente))
                .as("a recusa não cria pedido novo")
                .isEqualTo(1);
        assertThat(statusDoPedido(orderId))
                .as("e não cancela o aberto")
                .isEqualTo("AWAITING_PAYMENT");
    }

    





















    @Test
    void itensVoltamEmOrdemEstavel() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("10.00").productId());
        adicionarAoCarrinho(cliente.token(), criarPeca("20.00").productId());
        adicionarAoCarrinho(cliente.token(), criarPeca("30.00").productId());

        List<String> primeiraLeitura = idsDosItens(mockMvc.perform(checkout(cliente))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn());

        List<String> segundaLeitura = idsDosItens(mockMvc.perform(checkout(cliente))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andReturn());

        assertThat(segundaLeitura)
                .as("duas leituras do mesmo pedido devolvendo os itens em ordens diferentes "
                        + "fazem a lista dançar na tela do cliente sem nenhuma causa visível")
                .containsExactlyElementsOf(primeiraLeitura);
    }

    

    























































    @Test
    void duploCliqueNaoCriaDoisPedidos() throws Exception {
        Cliente cliente = novoClienteComEndereco(UfBrasileira.SP);
        adicionarAoCarrinho(cliente.token(), criarPeca("70.00").productId());

        CountDownLatch largada = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Resposta> respostas = new ArrayList<>();

        try {
            Future<Resposta> a = pool.submit(clicar(cliente, largada));
            Future<Resposta> b = pool.submit(clicar(cliente, largada));
            respostas.add(a.get(30, TimeUnit.SECONDS));
            respostas.add(b.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(pedidosDe(cliente))
                .as("dois cliques, um pedido: %s", respostas)
                .isEqualTo(1);

        assertThat(respostas).anyMatch(r -> r.status() == 201);

        for (Resposta r : respostas) {
            assertThat(r.status())
                    .as("resposta fora do contrato do duplo clique: %s", r)
                    .isIn(200, 201, 409);
            if (r.status() == 409) {
                assertThat(r.code())
                        .as("o perdedor da corrida tem que receber o MESMO código da rota "
                                + "sequencial. UNIQUENESS_VIOLATION significa que a violação "
                                + "do índice escapou do catch do passo 8 (ou que o catch pegou "
                                + "o tipo errado); resposta sem code significa que ela nem "
                                + "chegou a um @ExceptionHandler. Corpo: %s", r)
                        .isEqualTo("ORDER_ALREADY_OPEN");
            }
        }
    }

    

    private static final String TELEFONE_DO_CLIENTE = "11987654321";

    private record Cliente(String nome, String email, String token, UUID addressId, UUID userId) {}

    



    private record Peca(UUID productId, String titulo, UUID categoryId, CustomUserDetails dona) {}

    private record Resposta(int status, String code, String corpo) {
        @Override
        public String toString() {
            return "HTTP " + status + " code=" + code + " body=" + corpo;
        }
    }

    private Callable<Resposta> clicar(Cliente cliente, CountDownLatch largada) {
        return () -> {
            largada.countDown();
            if (!largada.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("a outra thread não chegou à largada");
            }
            MvcResult resultado = mockMvc.perform(checkout(cliente)).andReturn();
            String corpo = resultado.getResponse().getContentAsString();
            String code = corpo.isBlank() ? null : ler(corpo, "$.code");
            return new Resposta(resultado.getResponse().getStatus(), code, corpo);
        };
    }

    private org.springframework.test.web.servlet.RequestBuilder checkout(Cliente cliente) {
        return checkout(cliente, cliente.addressId());
    }

    private org.springframework.test.web.servlet.RequestBuilder checkout(Cliente cliente, UUID addressId) {
        return post("/me/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + cliente.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"addressId\":\"" + addressId + "\"}");
    }

    private int pedidosDe(Cliente cliente) {
        Integer total = jdbc.queryForObject(
                "SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, cliente.userId());
        return total == null ? 0 : total;
    }

    private UUID idDoPedido(MvcResult resultado) throws Exception {
        return UUID.fromString(ler(resultado.getResponse().getContentAsString(), "$.id"));
    }

    private List<String> idsDosItens(MvcResult resultado) throws Exception {
        return ler(resultado.getResponse().getContentAsString(), "$.items[*].productId");
    }

    















    private static <T> T ler(String corpo, String caminho) {
        return JsonPath.using(JSON_TOLERANTE).parse(corpo).read(caminho);
    }

    private static final Configuration JSON_TOLERANTE = Configuration.defaultConfiguration()
            .addOptions(Option.SUPPRESS_EXCEPTIONS, Option.DEFAULT_PATH_LEAF_TO_NULL);

    

    private Cliente novoClienteComEndereco(UfBrasileira uf) throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        String nome = "Cliente " + m;
        String email = "checkout-" + m + "@teste.local";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"senha-forte-123","phone":"%s"}"""
                                .formatted(nome, email, TELEFONE_DO_CLIENTE)))
                .andExpect(status().isCreated());

        String resposta = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senha-forte-123"}""".formatted(email)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = ler(resposta, "$.accessToken");

        UUID addressId = criarEndereco(token, uf, "Rua Um", "100", "Centro", "Cidade");
        UUID userId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", UUID.class, email);

        return new Cliente(nome, email, token, addressId, userId);
    }

    private UUID criarEndereco(String token, UfBrasileira uf, String rua, String numero,
                               String bairro, String cidade) throws Exception {
        String resposta = mockMvc.perform(post("/me/addresses")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"zipCode":"01310100","street":"%s","number":"%s","complement":null,
                                 "district":"%s","city":"%s","uf":"%s"}"""
                                .formatted(rua, numero, bairro, cidade, uf.name())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(ler(resposta, "$.id"));
    }

    

    private Peca criarPeca(String preco) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User dona = userRepository.save(User.createWithRole(
                "Dona " + m, "dona-" + m + "@teste.local", "hash", Role.SELLER));
        Category categoria = categoryRepository.save(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        String titulo = "Peça " + m;
        Product produto = productRepository.save(Product.create(dona, categoria, titulo,
                "desc", "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal(preco)));

        return new Peca(produto.getId(), titulo, categoria.getId(), new CustomUserDetails(dona));
    }

    private void adicionarAoCarrinho(String token, UUID productId) throws Exception {
        mockMvc.perform(post("/me/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + productId + "\"}"))
                .andExpect(status().isOk());
    }

    





    private void editarPeca(Peca peca, String preco, String titulo) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/seller/products/{id}", peca.productId())
                        .with(user(peca.dona()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","description":"desc","brand":"Marca","color":"azul",
                                 "size":"M","sizeLabel":null,"condition":"GOOD","price":%s,
                                 "categoryId":"%s"}"""
                                .formatted(titulo, preco, peca.categoryId())))
                .andExpect(status().isOk());
    }

    private String statusDoPedido(UUID orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    

    private String ruaDoPedido(UUID orderId) {
        return jdbc.queryForObject(
                "SELECT ship_street FROM orders WHERE id = ?", String.class, orderId);
    }
}
