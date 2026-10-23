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
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


























@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EscritaDiretaTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository userRepository;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired Clock clock;

    

    @Test
    void papelInvalidoEscritoPorSqlEhRecusado() {
        UUID id = seedUser(Role.SELLER).getId();

        assertThatThrownBy(() -> jdbc.update("UPDATE users SET role = 'SELER' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_users_role");
    }

    

    @Test
    void osTresPapeisReaisSaoAceitos() {
        UUID id = seedUser(Role.CLIENT).getId();

        for (Role papel : Role.values()) {
            assertThatCode(() -> jdbc.update("UPDATE users SET role = ? WHERE id = ?", papel.name(), id))
                    .as("papel %s deveria ser aceito", papel)
                    .doesNotThrowAnyException();
        }
    }

    

    @Test
    void condicaoInvalidaEscritaPorSqlEhRecusada() {
        UUID id = seedProduct();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE products SET product_condition = 'MEIA_BOCA' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_products_condition");
    }

    @Test
    void todasAsCondicoesReaisSaoAceitas() {
        UUID id = seedProduct();

        for (ProductCondition c : ProductCondition.values()) {
            assertThatCode(() -> jdbc.update(
                    "UPDATE products SET product_condition = ? WHERE id = ?", c.name(), id))
                    .as("condição %s deveria ser aceita", c)
                    .doesNotThrowAnyException();
        }
    }

    

    




    @Test
    void ufInexistenteEhRecusada() {
        UUID userId = seedUser(Role.CLIENT).getId();

        assertThatThrownBy(() -> inserirEndereco(userId, "XX"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_addresses_uf");
    }

    

    @Test
    void ufMinusculaEhRecusadaPeloBanco() {
        UUID userId = seedUser(Role.CLIENT).getId();

        assertThatThrownBy(() -> inserirEndereco(userId, "sp"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void as27UnidadesFederativasSaoAceitas() {
        UUID userId = seedUser(Role.CLIENT).getId();
        String[] ufs = {"AC","AL","AP","AM","BA","CE","DF","ES","GO","MA","MT","MS","MG",
                        "PA","PB","PR","PE","PI","RJ","RN","RS","RO","RR","SC","SP","SE","TO"};

        assertThat(ufs).hasSize(27);
        for (String uf : ufs) {
            assertThatCode(() -> inserirEndereco(userId, uf))
                    .as("UF %s deveria ser aceita", uf)
                    .doesNotThrowAnyException();
        }
    }

    

    
    @Test
    void doisCarrinhosParaOMesmoClienteSaoRecusados() {
        UUID userId = seedUser(Role.CLIENT).getId();
        inserirCarrinho(userId);

        assertThatThrownBy(() -> inserirCarrinho(userId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_carts_user");
    }

    



    @Test
    void aMesmaPecaDuasVezesNoMesmoCarrinhoEhRecusada() {
        UUID userId = seedUser(Role.CLIENT).getId();
        UUID cartId = inserirCarrinho(userId);
        UUID productId = seedProduct();
        inserirItem(cartId, productId);

        assertThatThrownBy(() -> inserirItem(cartId, productId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_cart_items_product");
    }

    






    @Test
    void aMesmaPecaEmCarrinhosDiferentesEhAceita() {
        UUID productId = seedProduct();
        UUID cartA = inserirCarrinho(seedUser(Role.CLIENT).getId());
        UUID cartB = inserirCarrinho(seedUser(Role.CLIENT).getId());

        inserirItem(cartA, productId);
        assertThatCode(() -> inserirItem(cartB, productId)).doesNotThrowAnyException();
    }

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    

    

    @Test
    void statusInvalidoDePedidoEscritoPorSqlEhRecusado() {
        UUID userId = seedUser(Role.CLIENT).getId();

        assertThatThrownBy(() -> inserirPedido(userId, "ESPERANDO_PAGAMENTO", "SP"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_orders_status");
    }

    


    @Test
    void osDoisStatusReaisDePedidoSaoAceitos() {
        assertThat(OrderStatus.values()).hasSize(2);

        for (OrderStatus status : OrderStatus.values()) {
            
            
            
            UUID userId = seedUser(Role.CLIENT).getId();
            assertThatCode(() -> inserirPedido(userId, status.name(), "SP"))
                    .as("status %s deveria ser aceito", status)
                    .doesNotThrowAnyException();
        }
    }

    




















    @Test
    void paidNaoEhAceitoAinda() {
        UUID userId = seedUser(Role.CLIENT).getId();
        UUID orderId = inserirPedido(userId, "AWAITING_PAYMENT", "SP");

        assertThatThrownBy(() -> jdbc.update("UPDATE orders SET status = 'PAID' WHERE id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_orders_status");
    }

    

    


    @Test
    void ufInvalidaNoSnapshotDoPedidoEhRecusada() {
        UUID userId = seedUser(Role.CLIENT).getId();

        assertThatThrownBy(() -> inserirPedido(userId, "AWAITING_PAYMENT", "XX"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_orders_ship_uf");
    }

    @Test
    void as27UfsSaoAceitasNoSnapshotDoPedido() {
        assertThat(UfBrasileira.values()).hasSize(27);

        for (UfBrasileira uf : UfBrasileira.values()) {
            UUID userId = seedUser(Role.CLIENT).getId();
            assertThatCode(() -> inserirPedido(userId, "AWAITING_PAYMENT", uf.name()))
                    .as("UF %s deveria ser aceita no snapshot do pedido", uf)
                    .doesNotThrowAnyException();
        }
    }

    

    

















    @Test
    void doisPedidosAbertosParaOMesmoClienteSaoRecusados() {
        UUID userId = seedUser(Role.CLIENT).getId();
        inserirPedido(userId, "AWAITING_PAYMENT", "SP");

        assertThatThrownBy(() -> inserirPedido(userId, "AWAITING_PAYMENT", "SP"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_orders_awaiting_per_user");
    }

    














    @Test
    void segundoPedidoEhAceitoQuandoOPrimeiroEstaExpirado() {
        UUID userId = seedUser(Role.CLIENT).getId();
        UUID primeiro = inserirPedido(userId, "AWAITING_PAYMENT", "SP");

        jdbc.update("UPDATE orders SET status = 'EXPIRED' WHERE id = ?", primeiro);

        assertThatCode(() -> inserirPedido(userId, "AWAITING_PAYMENT", "SP"))
                .doesNotThrowAnyException();

        
        assertThat(jdbc.queryForObject(
                "SELECT status FROM orders WHERE id = ?", String.class, primeiro))
                .isEqualTo("EXPIRED");
    }

    



    @Test
    void clientesDiferentesPodemTerPedidoAbertoAoMesmoTempo() {
        UUID clienteA = seedUser(Role.CLIENT).getId();
        UUID clienteB = seedUser(Role.CLIENT).getId();

        inserirPedido(clienteA, "AWAITING_PAYMENT", "SP");

        assertThatCode(() -> inserirPedido(clienteB, "AWAITING_PAYMENT", "SP"))
                .doesNotThrowAnyException();
    }

    

    


    @Test
    void pagamentoComTxidSemCopiaEColaEhRecusado() {
        UUID orderId = pedidoDeClienteNovo();

        assertThatThrownBy(() -> inserirPagamento(orderId, "TX-" + UUID.randomUUID(), null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_charge_pareada");
    }

    @Test
    void pagamentoComCopiaEColaSemTxidEhRecusado() {
        UUID orderId = pedidoDeClienteNovo();

        assertThatThrownBy(() -> inserirPagamento(orderId, null, "00020126BR.GOV.BCB.PIX"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_charge_pareada");
    }

    







    @Test
    void pagamentoRecemCriadoComOsDoisNulosEhAceito() {
        UUID orderId = pedidoDeClienteNovo();

        assertThatCode(() -> inserirPagamento(orderId, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void pagamentoComOsDoisPreenchidosEhAceito() {
        UUID orderId = pedidoDeClienteNovo();

        assertThatCode(() -> inserirPagamento(orderId, "TX-" + UUID.randomUUID(),
                "00020126BR.GOV.BCB.PIX"))
                .doesNotThrowAnyException();
    }

    

    



    @Test
    void doisPagamentosComOMesmoTxidSaoRecusados() {
        String txid = "TX-" + UUID.randomUUID();
        inserirPagamento(pedidoDeClienteNovo(), txid, "00020126BR.GOV.BCB.PIX");

        assertThatThrownBy(() -> inserirPagamento(pedidoDeClienteNovo(), txid,
                "00020126BR.GOV.BCB.PIX"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uk_payments_txid");
    }

    













    @Test
    void doisPagamentosComTxidNuloConvivem() {
        inserirPagamento(pedidoDeClienteNovo(), null, null);

        assertThatCode(() -> inserirPagamento(pedidoDeClienteNovo(), null, null))
                .doesNotThrowAnyException();
    }

    

    




















    @Test
    void osDoisIndicesUnicosDaFase3SaoParciais() {
        var indices = jdbc.queryForList("""
                SELECT i.relname AS indice,
                       pg_get_expr(x.indpred, x.indrelid) AS filtro
                  FROM pg_index x
                  JOIN pg_class i ON i.oid = x.indexrelid
                 WHERE i.relname IN ('uk_orders_awaiting_per_user', 'uk_payments_txid')
                   AND x.indisunique
                """);

        assertThat(indices).extracting(l -> l.get("indice"))
                .as("os dois índices únicos da V3 precisam existir")
                .containsExactlyInAnyOrder("uk_orders_awaiting_per_user", "uk_payments_txid");

        
        
        assertThat(indices)
                .as("índice sem filtro é índice CHEIO: a parcialidade caiu. "
                  + "Reponha o WHERE na V3 — não relaxe esta asserção.")
                .allSatisfy(l -> assertThat(l.get("filtro")).isNotNull());

        assertThat(filtroDe(indices, "uk_orders_awaiting_per_user"))
                .as("um pedido ABERTO por cliente — o filtro precisa recortar o status, "
                  + "senão o cliente com pedido vencido nunca mais compra")
                .contains("AWAITING_PAYMENT");

        assertThat(filtroDe(indices, "uk_payments_txid"))
                .as("a unicidade vale só para cobrança existente; txid nulo é a "
                  + "intenção registrada antes do efeito externo")
                .contains("txid IS NOT NULL");
    }

    private String filtroDe(java.util.List<java.util.Map<String, Object>> indices, String nome) {
        return indices.stream()
                .filter(l -> nome.equals(l.get("indice")))
                .map(l -> (String) l.get("filtro"))
                .findFirst()
                .orElse(null);
    }

    

    





















    @Test
    void nenhumaTabelaDePedidoReferenciaAddresses() {
        var fks = jdbc.queryForList("""
                SELECT c.conname, c.conrelid::regclass::text AS tabela
                  FROM pg_constraint c
                 WHERE c.contype = 'f'
                   AND c.conrelid::regclass::text IN ('orders','order_items','payments')
                   AND c.confrelid::regclass::text = 'addresses'
                """);

        assertThat(fks)
                .as("nenhuma tabela da Fase 3 pode ter FK para addresses — "
                  + "o pedido COPIA o endereço (V2 :19-23). Tire a FK, não a asserção.")
                .isEmpty();
    }

    











    @Test
    void aConsultaAoCatalogoEnxergaAsFksQueDeFatoExistem() {
        var fks = jdbc.queryForList("""
                SELECT c.conname, c.conrelid::regclass::text AS tabela
                  FROM pg_constraint c
                 WHERE c.contype = 'f'
                   AND c.conrelid::regclass::text IN ('orders','order_items','payments')
                   AND c.confrelid::regclass::text = 'users'
                """);

        assertThat(fks).extracting(linha -> linha.get("conname"))
                .as("a consulta precisa enxergar as FKs que existem, senão o teste "
                  + "da invariante fica verde por cegueira")
                .contains("fk_orders_user");
    }

    





    @Test
    void enderecoApagadoNaoLevaOPedidoJunto() {
        UUID userId = seedUser(Role.CLIENT).getId();
        UUID addressId = inserirEndereco(userId, "SP");
        UUID orderId = inserirPedido(userId, "AWAITING_PAYMENT", "SP");

        
        assertThatCode(() -> jdbc.update("DELETE FROM addresses WHERE id = ?", addressId))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM addresses WHERE id = ?", Integer.class, addressId))
                .isZero();

        
        var pedido = jdbc.queryForMap(
                "SELECT ship_street, ship_city, ship_uf FROM orders WHERE id = ?", orderId);
        assertThat(pedido).containsEntry("ship_street", "Av. Paulista")
                          .containsEntry("ship_city", "São Paulo")
                          .containsEntry("ship_uf", "SP");
    }

    

    private User seedUser(Role role) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(User.createWithRole(
                "P " + m, "p-" + m + "@teste.local", "hash", role));
    }

    private UUID seedProduct() {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User seller = seedUser(Role.SELLER);
        Category cat = categoryRepository.saveAndFlush(
                Category.create("Cat " + m, "cat-" + m, CategorySizeType.ALPHA));
        return productRepository.saveAndFlush(Product.create(seller, cat, "Peça", "desc",
                "Marca", "M", null, "azul", ProductCondition.GOOD, new BigDecimal("50.00"))).getId();
    }

    private UUID inserirEndereco(UUID userId, String uf) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO addresses (id, user_id, zip_code, street, number, district,
                                       city, uf, created_at, updated_at)
                VALUES (?, ?, '01310100', 'Av. Paulista', '1000', 'Bela Vista', 'São Paulo', ?, ?, ?)
                """, id, userId, uf,
                java.sql.Timestamp.from(clock.instant()), java.sql.Timestamp.from(clock.instant()));
        return id;
    }

    private UUID inserirCarrinho(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO carts (id, user_id, created_at, updated_at) VALUES (?, ?, ?, ?)",
                id, userId, java.sql.Timestamp.from(clock.instant()),
                java.sql.Timestamp.from(clock.instant()));
        return id;
    }

    private void inserirItem(UUID cartId, UUID productId) {
        jdbc.update("""
                INSERT INTO cart_items (id, cart_id, product_id, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), cartId, productId,
                java.sql.Timestamp.from(clock.instant()), java.sql.Timestamp.from(clock.instant()));
    }

    





    private UUID inserirPedido(UUID userId, String status, String uf) {
        UUID id = UUID.randomUUID();
        java.sql.Timestamp agora = java.sql.Timestamp.from(clock.instant());
        java.sql.Timestamp vence = java.sql.Timestamp.from(clock.instant().plus(java.time.Duration.ofMinutes(30)));
        jdbc.update("""
                INSERT INTO orders (id, user_id, status, subtotal, shipping_cost,
                                    ship_recipient_name, ship_phone, ship_zip_code,
                                    ship_street, ship_number, ship_complement,
                                    ship_district, ship_city, ship_uf,
                                    expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 50.00, 19.90,
                        'Maria Silva', '11999998888', '01310100',
                        'Av. Paulista', '1000', NULL,
                        'Bela Vista', 'São Paulo', ?,
                        ?, ?, ?)
                """, id, userId, status, uf, vence, agora, agora);
        return id;
    }

    


    private UUID pedidoDeClienteNovo() {
        return inserirPedido(seedUser(Role.CLIENT).getId(), "AWAITING_PAYMENT", "SP");
    }

    











    private void inserirPagamento(UUID orderId, String txid, String copyPaste) {
        java.sql.Timestamp agora = java.sql.Timestamp.from(clock.instant());
        java.util.Set<String> colunas = new java.util.HashSet<>(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'payments'
                """, String.class));
        if (!colunas.contains("status")) {
            jdbc.update("""
                    INSERT INTO payments (id, order_id, txid, pix_copy_paste, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, UUID.randomUUID(), orderId, txid, copyPaste, agora, agora);
            return;
        }
        jdbc.update("""
                INSERT INTO payments (id, order_id, txid, pix_copy_paste, created_at, updated_at,
                                      status, amount, order_nsu)
                VALUES (?, ?, ?, ?, ?, ?, ?, 69.90, ?)
                """, UUID.randomUUID(), orderId, txid, copyPaste, agora, agora,
                copyPaste != null ? "OPEN" : "PENDING_CONFIRMATION", orderId.toString());
    }

    




















}
