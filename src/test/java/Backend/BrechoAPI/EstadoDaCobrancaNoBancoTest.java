package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.Role;
import Backend.BrechoAPI.repository.UserRepository;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Column;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowable;





























































@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EstadoDaCobrancaNoBancoTest {

    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired UserRepository userRepository;
    @Autowired Clock clock;

    private static final List<String> OS_TRES = List.of("PENDING_CONFIRMATION", "OPEN", "NOT_CREATED");

    

    @Test
    void a1_osTresValoresDaCobrancaSaoAceitos() {
        exigirV4("provar os três valores do status da cobrança");

        for (String status : OS_TRES) {
            UUID orderId = pedidoDeClienteNovo();
            assertThatCode(() -> inserirCobranca(orderId, status, comArtefatoSe(status)))
                    .as("A1 — o status %s da cobrança tem de ser aceito pelo ck_payments_status "
                            + "(ADR 0016 §3.3)", status)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void a1_valorInventadoEhRecusadoPeloCheck() {
        exigirV4("provar que o CHECK recusa valor inventado");
        UUID orderId = pedidoDeClienteNovo();

        Throwable erro = catchThrowable(() -> inserirCobranca(orderId, "EM_ANALISE", false));

        assertThat(erro)
                .as("A1 — um status que nenhum escritor produz tem de ser recusado pelo BANCO "
                        + "(o INSERT de madrugada, o script de correção)")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_status");
    }

    











    @Test
    void a1_confirmedAindaNaoEhAceito() {
        exigirV4("provar que CONFIRMED ainda não é aceito");
        UUID orderId = pedidoDeClienteNovo();

        Throwable erro = catchThrowable(() -> inserirCobranca(orderId, "CONFIRMED", true));

        assertThat(erro)
                .as("A1 / Q7 — CONFIRMED não tem escritor nesta sprint (ADR 0016 §3.13); o CHECK "
                        + "tem de recusá-lo até nascer com PAID e Commission")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_status");
    }

    







    @Test
    void a1_paymentStatusEOCheckAndamJuntos() {
        Field campo = campoQueMapeiaAColuna("status");
        assertThat(campo)
                .as("A1, gêmeo do 'juntos' — Payment ainda não tem o campo que mapeia a coluna "
                        + "status (ADR 0016 §2: 'a cobrança ganha status'). Vermelho de produto "
                        + "da DV0-c")
                .isNotNull();
        Class<?> tipo = campo.getType();
        assertThat(tipo.isEnum())
                .as("o status da cobrança é enum Java + CHECK (SISTEMA.md §7); veio %s", tipo)
                .isTrue();
        assertThat(tipo.getSimpleName())
                .as("o nome do tipo é o da ADR 0016 §2")
                .isEqualTo("PaymentStatus");

        List<String> valores = Arrays.stream(tipo.getEnumConstants())
                .map(v -> ((Enum<?>) v).name()).toList();
        assertThat(valores)
                .as("PaymentStatus termina a DV0 com os TRÊS valores que têm transição nesta "
                        + "sprint (ADR 0016 §3.3, §3.13)")
                .containsExactlyInAnyOrderElementsOf(OS_TRES);

        exigirV4("conferir o enum contra o CHECK");
        for (String status : valores) {
            UUID orderId = pedidoDeClienteNovo();
            assertThatCode(() -> inserirCobranca(orderId, status, comArtefatoSe(status)))
                    .as("PaymentStatus.%s existe no Java e o ck_payments_status o recusa: os dois "
                            + "lados divergiram. Enum e CHECK mudam JUNTOS, na mesma migration",
                            status)
                    .doesNotThrowAnyException();
        }
    }

    

    @Test
    void a2_openSemArtefatoEhRecusado() {
        exigirV4("provar o casamento status × artefato");
        UUID orderId = pedidoDeClienteNovo();

        Throwable erro = catchThrowable(() -> inserirCobranca(orderId, "OPEN", false));

        assertThat(erro)
                .as("A2 — OPEN quer dizer 'há link'; OPEN sem pix_copy_paste é um link que não "
                        + "existe (ADR 0016 §3.5)")
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_payments_status_artefato");
    }

    @Test
    void a2_pendenteOuNaoCriadaComArtefatoEhRecusada() {
        exigirV4("provar o casamento status × artefato");

        for (String status : List.of("PENDING_CONFIRMATION", "NOT_CREATED")) {
            UUID orderId = pedidoDeClienteNovo();
            Throwable erro = catchThrowable(() -> inserirCobranca(orderId, status, true));
            assertThat(erro)
                    .as("A2 — %s COM artefato: ou o artefato é de outra tentativa, ou a "
                            + "transição que o gravou esqueceu o status", status)
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_payments_status_artefato");
        }
    }

    

    @Test
    void a2_openComArtefatoEhAceita() {
        exigirV4("provar o gêmeo do casamento status × artefato");
        UUID orderId = pedidoDeClienteNovo();

        assertThatCode(() -> inserirCobranca(orderId, "OPEN", true))
                .as("A2, gêmeo — OPEN com artefato é o estado depois de K2")
                .doesNotThrowAnyException();
    }

    

    @Test
    void a3_segundaCobrancaVivaDoMesmoPedidoEhRecusadaPeloIndice() {
        exigirV4("provar o índice parcial da cobrança viva");

        UUID pedidoComPendente = pedidoDeClienteNovo();
        inserirCobranca(pedidoComPendente, "PENDING_CONFIRMATION", false);
        Throwable segundaPendente = catchThrowable(
                () -> inserirCobranca(pedidoComPendente, "PENDING_CONFIRMATION", false));

        UUID pedidoComAberta = pedidoDeClienteNovo();
        inserirCobranca(pedidoComAberta, "OPEN", true);
        Throwable pendenteDepoisDeAberta = catchThrowable(
                () -> inserirCobranca(pedidoComAberta, "PENDING_CONFIRMATION", false));

        assertThat(sqlStateDe(segundaPendente))
                .as("A3 — a segunda cobrança VIVA do mesmo pedido é o perdedor de K1: o banco "
                        + "tem de recusar com 23505. Veio: %s", segundaPendente)
                .isEqualTo("23505");
        assertThat(segundaPendente)
                .as("A3 — e nomeando uk_payments_order: é por esse NOME que K1 reconhece "
                        + "'perdi' (ADR 0016 §2). Outro nome aqui é o 500 de B4(i), não o 200")
                .hasMessageContaining("uk_payments_order");
        assertThat(sqlStateDe(pendenteDepoisDeAberta))
                .as("A3 — OPEN também é viva: um índice filtrado só em PENDING_CONFIRMATION "
                        + "deixaria uma pendente nascer ao lado de um link aberto. Veio: %s",
                        pendenteDepoisDeAberta)
                .isEqualTo("23505");
        assertThat(pendenteDepoisDeAberta).hasMessageContaining("uk_payments_order");
    }

    

    @Test
    void a3_naoCriadaMaisUmaVivaSaoAceitas() {
        exigirV4("provar o gêmeo da parcialidade");
        UUID orderId = pedidoDeClienteNovo();
        inserirCobranca(orderId, "NOT_CREATED", false);

        assertThatCode(() -> inserirCobranca(orderId, "PENDING_CONFIRMATION", false))
                .as("A3, gêmeo — NOT_CREATED não é viva: a retomada depois dela faz K1 de "
                        + "novo, LINHA NOVA (ADR 0016 §3.1, Q6)")
                .doesNotThrowAnyException();
    }

    @Test
    void a3_duasNaoCriadasSaoAceitas() {
        exigirV4("provar que tentativas mortas se acumulam");
        UUID orderId = pedidoDeClienteNovo();
        inserirCobranca(orderId, "NOT_CREATED", false);

        assertThatCode(() -> inserirCobranca(orderId, "NOT_CREATED", false))
                .as("A3 — toda tentativa morta FICA (o registro de que ela falhou); um índice "
                        + "sobre (order_id, status) recusaria a segunda")
                .doesNotThrowAnyException();
    }

    

    















    @Test
    void a4_pedidoComTentativaNaoEhApagado() {
        UUID orderId = pedidoDeClienteNovo();
        inserirCobrancaQualquerQueSejaOEsquema(orderId);

        Throwable erro = catchThrowable(() -> jdbc.update("DELETE FROM orders WHERE id = ?", orderId));

        assertThat(erro)
                .as("A4 — o DELETE de um pedido COM tentativa de cobrança PASSOU: a cascata "
                        + "levou as tentativas e o rastro junto. A V4 troca fk_payments_order para "
                        + "ON DELETE RESTRICT (ADR 0016 §3.8 item 8, §3.12)")
                .isNotNull();
        assertThat(sqlStateDe(erro))
                .as("A4 — a recusa é de FK (23503). Veio: %s", erro)
                .isEqualTo("23503");
        assertThat(erro)
                .as("A4, gêmeo — e é a FK da COBRANÇA que recusa: 23503 de outra FK deixaria "
                        + "verde com a cascata de payments intacta")
                .hasMessageContaining("fk_payments_order");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payments WHERE order_id = ?",
                Integer.class, orderId))
                .as("e a tentativa continua lá")
                .isEqualTo(1);
    }

    

    @Test
    void a5_rastroPelaMetadeEhRecusado() {
        exigirV4("provar o rastro da decisão humana");
        UUID adminId = seedUser(Role.ADMIN).getId();
        Timestamp agora = Timestamp.from(clock.instant());

        List<Object[]> metades = List.of(
                new Object[] {adminId, null, null},
                new Object[] {null, agora, null},
                new Object[] {null, null, "NOT_CREATED"},
                new Object[] {adminId, agora, null},
                new Object[] {null, agora, "NOT_CREATED"});

        for (Object[] m : metades) {
            UUID orderId = pedidoDeClienteNovo();
            Throwable erro = catchThrowable(() -> inserirCobrancaRevisada(orderId, m[0], m[1], m[2]));
            assertThat(erro)
                    .as("A5 — rastro pela metade (reviewed_by=%s, reviewed_at=%s, "
                            + "review_outcome=%s): 'quem, quando, para qual estado' chegam juntos "
                            + "ou não chegam (ADR 0016 §3.6)", m[0], m[1], m[2])
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_payments_review_completo");
        }
    }

    


    @Test
    void a5_resultadoDaRevisaoForaDosDoisValoresEhRecusado() {
        exigirV4("provar os valores do resultado da revisão");
        UUID adminId = seedUser(Role.ADMIN).getId();
        Timestamp agora = Timestamp.from(clock.instant());

        for (String fora : List.of("PENDING_CONFIRMATION", "CONFIRMED", "TALVEZ")) {
            UUID orderId = pedidoDeClienteNovo();
            Throwable erro = catchThrowable(
                    () -> inserirCobrancaRevisada(orderId, adminId, agora, fora));
            assertThat(sqlStateDe(erro))
                    .as("A5 — review_outcome '%s' fora de NOT_CREATED/OPEN tem de ser recusado "
                            + "por CHECK (23514). Veio: %s", fora, erro)
                    .isEqualTo("23514");
        }
    }

    @Test
    void a5_rastroCompletoEhAceito() {
        exigirV4("provar o gêmeo do rastro");
        UUID adminId = seedUser(Role.ADMIN).getId();
        UUID orderId = pedidoDeClienteNovo();

        assertThatCode(() -> inserirCobrancaRevisada(orderId, adminId,
                Timestamp.from(clock.instant()), "NOT_CREATED"))
                .as("A5, gêmeo — o rastro completo de K5 é aceito; sem isto, um CHECK que "
                        + "recusasse qualquer rastro passaria nos dois de cima")
                .doesNotThrowAnyException();
    }

    

    @Test
    void a6_cobrancaSemAmountOuSemOrderNsuEhRecusada() {
        exigirV4("provar amount e order_nsu obrigatórios");
        Timestamp agora = Timestamp.from(clock.instant());

        UUID semAmount = pedidoDeClienteNovo();
        Throwable erroAmount = catchThrowable(() -> jdbc.update("""
                INSERT INTO payments (id, order_id, status, amount, order_nsu, created_at, updated_at)
                VALUES (?, ?, 'PENDING_CONFIRMATION', NULL, ?, ?, ?)
                """, UUID.randomUUID(), semAmount, semAmount.toString(), agora, agora));

        UUID semNsu = pedidoDeClienteNovo();
        Throwable erroNsu = catchThrowable(() -> jdbc.update("""
                INSERT INTO payments (id, order_id, status, amount, order_nsu, created_at, updated_at)
                VALUES (?, ?, 'PENDING_CONFIRMATION', 69.90, NULL, ?, ?)
                """, UUID.randomUUID(), semNsu, agora, agora));

        assertThat(sqlStateDe(erroAmount))
                .as("A6 — cobrança sem amount: o valor que nós escrevemos ANTES de chamar é o "
                        + "que amarra a cobrança a nós (ADR 0016 §3.13). 23502 esperado; veio %s",
                        erroAmount)
                .isEqualTo("23502");
        assertThat(erroAmount).hasMessageContaining("amount");
        assertThat(sqlStateDe(erroNsu))
                .as("A6 — cobrança sem order_nsu. 23502 esperado; veio %s", erroNsu)
                .isEqualTo("23502");
        assertThat(erroNsu).hasMessageContaining("order_nsu");
    }

    

    









    @Test
    void a7_aV4PreencheAsLinhasQueJaExistiam() throws Exception {
        String schema = "qa_a7_" + UUID.randomUUID().toString().substring(0, 8);
        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);

        try {
            flyway(hikari, schema, "3").migrate();

            UUID userId = UUID.randomUUID();
            UUID comArtefato = UUID.randomUUID();
            UUID semArtefato = UUID.randomUUID();
            Timestamp agora = Timestamp.from(clock.instant());
            jdbc.update(("INSERT INTO %s.users (id, name, email, password_hash, role, created_at,"
                    + " updated_at) VALUES (?, 'A7', ?, 'hash', 'CLIENT', ?, ?)").formatted(schema),
                    userId, "a7-" + userId + "@teste.local", agora, agora);
            
            inserirPedidoNoSchema(schema, comArtefato, userId, "EXPIRED", "50.00", "19.90", agora);
            inserirPedidoNoSchema(schema, semArtefato, userId, "AWAITING_PAYMENT", "70.00", "24.90",
                    agora);
            jdbc.update(("INSERT INTO %s.payments (id, order_id, txid, pix_copy_paste, created_at,"
                    + " updated_at) VALUES (?, ?, 'TX-A7', '000201-A7', ?, ?)").formatted(schema),
                    UUID.randomUUID(), comArtefato, agora, agora);
            jdbc.update(("INSERT INTO %s.payments (id, order_id, txid, pix_copy_paste, created_at,"
                    + " updated_at) VALUES (?, ?, NULL, NULL, ?, ?)").formatted(schema),
                    UUID.randomUUID(), semArtefato, agora, agora);

            Flyway ateOFim = flyway(hikari, schema, null);
            ateOFim.migrate();
            MigrationInfo atual = ateOFim.info().current();

            assertThat(atual.getVersion().getMajor().intValue())
                    .as("A7 — A V4 AINDA NÃO EXISTE: migrar até o fim parou na versão %s. O "
                            + "preenchimento das linhas que já existiam (ADR 0016 §3.8 itens 1 e 5) "
                            + "é da V4 — vermelho de produto da DV0-c", atual.getVersion())
                    .isGreaterThanOrEqualTo(4);

            Map<String, Object> aberta = jdbc.queryForMap(
                    "SELECT * FROM %s.payments WHERE order_id = ?".formatted(schema), comArtefato);
            Map<String, Object> pendente = jdbc.queryForMap(
                    "SELECT * FROM %s.payments WHERE order_id = ?".formatted(schema), semArtefato);

            assertThat(aberta.get("status"))
                    .as("A7 — linha antiga COM artefato vira OPEN (§3.8 item 1)")
                    .isEqualTo("OPEN");
            assertThat(pendente.get("status"))
                    .as("A7 — linha antiga SEM artefato vira PENDING_CONFIRMATION: o lado seguro, "
                            + "ela pode ter tido chamada perdida e vai para a fila (§3.8 item 1)")
                    .isEqualTo("PENDING_CONFIRMATION");
            assertThat((BigDecimal) aberta.get("amount"))
                    .as("A7 — amount = subtotal + shipping_cost (§3.8 item 5)")
                    .isEqualByComparingTo("69.90");
            assertThat((BigDecimal) pendente.get("amount")).isEqualByComparingTo("94.90");
            assertThat(aberta.get("order_nsu"))
                    .as("A7 — order_nsu = order_id::text (§3.8 item 5)")
                    .isEqualTo(comArtefato.toString());
            assertThat(pendente.get("order_nsu")).isEqualTo(semArtefato.toString());
        } finally {
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private static Flyway flyway(HikariDataSource hikari, String schema, String alvo) {
        var config = Flyway.configure()
                .dataSource(hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (alvo != null) config.target(alvo);
        return config.load();
    }

    private void inserirPedidoNoSchema(String schema, UUID id, UUID userId, String status,
                                       String subtotal, String frete, Timestamp agora) {
        jdbc.update(("""
                INSERT INTO %s.orders (id, user_id, status, subtotal, shipping_cost,
                                    ship_recipient_name, ship_phone, ship_zip_code,
                                    ship_street, ship_number, ship_complement,
                                    ship_district, ship_city, ship_uf,
                                    expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'Maria', '11999998888', '01310100', 'Av. Paulista', '1000',
                        NULL, 'Bela Vista', 'São Paulo', 'SP', ?, ?, ?)
                """).formatted(schema), id, userId, status, new BigDecimal(subtotal),
                new BigDecimal(frete), agora, agora, agora);
    }

    

    private Set<String> colunasDePayments() {
        return new LinkedHashSet<>(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'payments'
                """, String.class));
    }

    private void exigirV4(String paraQue) {
        assertThat(colunasDePayments())
                .as("A V4 AINDA NÃO EXISTE (ADR 0016 §3.8): payments não tem as colunas da "
                        + "cobrança. Sem elas não há como " + paraQue + ". Vermelho de produto da "
                        + "DV0-c — quem o fecha é a DV0-c-impl")
                .contains("status", "amount", "order_nsu", "reviewed_by", "reviewed_at",
                        "review_outcome");
    }

    


    static Field campoQueMapeiaAColuna(String coluna) {
        for (Field f : Payment.class.getDeclaredFields()) {
            Column c = f.getAnnotation(Column.class);
            String fisica = c != null && !c.name().isEmpty() ? c.name() : snake(f.getName());
            if (fisica.equals(coluna)) return f;
        }
        return null;
    }

    static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static boolean comArtefatoSe(String status) {
        return "OPEN".equals(status);
    }

    private void inserirCobranca(UUID orderId, String status, boolean comArtefato) {
        Timestamp agora = Timestamp.from(clock.instant());
        String txid = comArtefato ? "TX-" + UUID.randomUUID() : null;
        String artefato = comArtefato ? "00020126BR.GOV.BCB.PIX-" + UUID.randomUUID() : null;
        jdbc.update("""
                INSERT INTO payments (id, order_id, status, amount, order_nsu, txid, pix_copy_paste,
                                      created_at, updated_at)
                VALUES (?, ?, ?, 69.90, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, status, orderId.toString(), txid, artefato,
                agora, agora);
    }

    private void inserirCobrancaRevisada(UUID orderId, Object reviewedBy, Object reviewedAt,
                                         Object reviewOutcome) {
        Timestamp agora = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO payments (id, order_id, status, amount, order_nsu, created_at, updated_at,
                                      reviewed_by, reviewed_at, review_outcome)
                VALUES (?, ?, 'NOT_CREATED', 69.90, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), orderId, orderId.toString(), agora, agora,
                reviewedBy, reviewedAt, reviewOutcome);
    }

    

    private void inserirCobrancaQualquerQueSejaOEsquema(UUID orderId) {
        Timestamp agora = Timestamp.from(clock.instant());
        Set<String> colunas = colunasDePayments();
        List<String> nomes = new ArrayList<>(List.of("id", "order_id", "created_at", "updated_at"));
        List<Object> valores = new ArrayList<>(List.of(UUID.randomUUID(), orderId, agora, agora));
        if (colunas.contains("status")) { nomes.add("status"); valores.add("PENDING_CONFIRMATION"); }
        if (colunas.contains("amount")) { nomes.add("amount"); valores.add(new BigDecimal("69.90")); }
        if (colunas.contains("order_nsu")) { nomes.add("order_nsu"); valores.add(orderId.toString()); }
        String marcadores = String.join(", ", nomes.stream().map(n -> "?").toList());
        jdbc.update("INSERT INTO payments (" + String.join(", ", nomes) + ") VALUES ("
                + marcadores + ")", valores.toArray());
    }

    static String sqlStateDe(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) return sql.getSQLState();
            if (t.getCause() == t) break;
        }
        return null;
    }

    private User seedUser(Role role) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.createWithRole("Teste " + m, "a-" + m + "@teste.local",
                "hash", role));
    }

    private UUID pedidoDeClienteNovo() {
        UUID userId = seedUser(Role.CLIENT).getId();
        UUID id = UUID.randomUUID();
        Timestamp agora = Timestamp.from(clock.instant());
        Timestamp vence = Timestamp.from(clock.instant().plus(Duration.ofMinutes(30)));
        jdbc.update("""
                INSERT INTO orders (id, user_id, status, subtotal, shipping_cost,
                                    ship_recipient_name, ship_phone, ship_zip_code,
                                    ship_street, ship_number, ship_complement,
                                    ship_district, ship_city, ship_uf,
                                    expires_at, created_at, updated_at)
                VALUES (?, ?, 'AWAITING_PAYMENT', 50.00, 19.90,
                        'Maria Silva', '11999998888', '01310100',
                        'Av. Paulista', '1000', NULL,
                        'Bela Vista', 'São Paulo', 'SP',
                        ?, ?, ?)
                """, id, userId, vence, agora, agora);
        return id;
    }

    


















































































































































}
