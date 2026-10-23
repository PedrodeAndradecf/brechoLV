package Backend.BrechoAPI;

import Backend.BrechoAPI.PortaDeCobrancaControlavel.Modo;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
















































@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PortaDeCobrancaControlavel.Config.class})
@TestPropertySource(properties = "checkout.order-ttl=PT30M")
class FilaDeCobrancasPendentesTest extends ApoioDeCobrancaHttp {

    

    


    @Test
    void e1_aFilaDevolveAsPendentesMaisVelhasQueX() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        UUID cobranca = idDe(ultimaTentativaDe(orderId));
        envelhecer(cobranca, VELHA);
        Map<String, Object> linha = tentativa(cobranca);

        Resposta fila = lerFila(novoAdmin().token(), null);

        assertThat(fila.status())
                .as("E1 — GET %s como ADMIN responde 200 (ADR 0016 §3.4). Veio: %s",
                        ROTA_DA_FILA, fila)
                .isEqualTo(200);
        List<Object> folhas = folhasDoItemQueCita(itensDe(fila), cobranca);
        assertThat(folhas)
                .as("E1 — a cobrança PENDING_CONFIRMATION há mais de X (%s) sai na fila", cobranca)
                .isNotEmpty();
        assertThat(textos(folhas))
                .as("E1 — o item traz o id do pedido, o order_nsu e o telefone do snapshot do "
                        + "pedido (para falar com o cliente)")
                .contains(orderId.toString(), String.valueOf(linha.get("order_nsu")),
                        TELEFONE_DO_CLIENTE);
        assertThat(folhas)
                .as("E1 — o item traz o amount (%s)", linha.get("amount"))
                .anySatisfy(f -> assertThat(comoDinheiro(f))
                        .isNotNull().isEqualByComparingTo((BigDecimal) linha.get("amount")));
        Instant criada = ((Timestamp) linha.get("created_at")).toInstant().truncatedTo(ChronoUnit.MILLIS);
        assertThat(folhas)
                .as("E1 — o item traz o created_at (a idade): %s", criada)
                .anySatisfy(f -> assertThat(comoInstante(f)).isEqualTo(criada));
    }

    


    @Test
    void e1_controle_pendenteMaisNovaQueXNaoSai() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        UUID nova = idDe(ultimaTentativaDe(orderId));

        Resposta fila = lerFila(novoAdmin().token(), null);

        assertThat(fila.status())
                .as("guarda: a fila responde 200 — sem isto 'não sai' é vácuo. Veio: %s", fila)
                .isEqualTo(200);
        assertThat(folhasDoItemQueCita(itensDe(fila), nova))
                .as("E1, controle — a pendente MAIS NOVA que X não sai na fila")
                .isEmpty();
    }

    

    





















    @Test
    void e2_oArameNenhumaFormaDeChamarAFilaDevolveOQueNaoEhPendenteAntiga() throws Exception {
        Admin operador = novoAdmin();

        Cliente cAberta = clienteComCarrinho("70.00");
        Resposta criada = postarCheckout(cAberta);
        assertThat(criada.status()).as("guarda: %s", criada).isEqualTo(201);
        UUID aberta = idDe(ultimaTentativaDe(UUID.fromString(criada.id())));
        envelhecer(aberta, VELHA);

        UUID pedidoK3 = pedidoDepoisDeFalha(clienteComCarrinho("70.00"), Modo.FALHA_CERTA);
        UUID naoCriadaK3 = idDe(ultimaTentativaDe(pedidoK3));
        envelhecer(naoCriadaK3, VELHA);

        UUID pedidoNova = pedidoDepoisDeFalha(clienteComCarrinho("70.00"), Modo.FALHA_CRUA);
        UUID pendenteNova = idDe(ultimaTentativaDe(pedidoNova));

        UUID pedidoVelha = pedidoDepoisDeFalha(clienteComCarrinho("70.00"), Modo.FALHA_CRUA);
        UUID pendenteVelha = idDe(ultimaTentativaDe(pedidoVelha));
        envelhecer(pendenteVelha, VELHA);

        UUID semCobrancaVencido = pedidoPorSql("AWAITING_PAYMENT", Duration.ofMinutes(-5));
        UUID semCobranca = pedidoPorSql("AWAITING_PAYMENT", Duration.ofMinutes(30));
        UUID expirado = pedidoPorSql("EXPIRED", Duration.ofMinutes(-5));

        UUID pedidoK5 = pedidoDepoisDeFalha(clienteComCarrinho("70.00"), Modo.FALHA_CRUA);
        UUID naoCriadaK5 = idDe(ultimaTentativaDe(pedidoK5));
        envelhecer(naoCriadaK5, VELHA);
        decidirComoPassoDoRoteiro(operador, naoCriadaK5);

        List<String> formas = new ArrayList<>();
        formas.add(null);
        for (String s : List.of("OPEN", "NOT_CREATED", "AWAITING_PAYMENT", "PAID", "CONFIRMED")) {
            formas.add("?status=" + s);
        }

        Map<String, Resposta> respostas = new LinkedHashMap<>();
        for (String forma : formas) {
            respostas.put(forma == null ? "(sem parâmetro)" : forma, lerFila(operador.token(), forma));
        }

        assertThat(respostas.get("(sem parâmetro)").status())
                .as("guarda: a fila, sem parâmetro, responde 200 — sem leitor não há arame. "
                        + "Veio: %s", respostas.get("(sem parâmetro)"))
                .isEqualTo(200);

        List<String> violacoes = new ArrayList<>();
        List<String> semAPendenteVelha = new ArrayList<>();
        for (var e : respostas.entrySet()) {
            Resposta r = e.getValue();
            if (!r.doisXX()) continue;
            List<Object> itens = itensDe(r);
            for (Object item : itens) {
                String motivo = porQueNaoEhPendenteAntiga(item);
                if (motivo != null) violacoes.add(e.getKey() + " → " + motivo + " :: " + item);
            }
            if (folhasDoItemQueCita(itens, pendenteVelha).isEmpty()) {
                semAPendenteVelha.add(e.getKey());
            }
        }

        assertThat(violacoes)
                .as("E2, O ARAME (portão do par PJ) — nenhuma forma de chamar a fila devolve item "
                        + "que não seja cobrança PENDING_CONFIRMATION há mais de X. Plantados: "
                        + "aberta=%s naoCriadaK3=%s naoCriadaK5=%s pendenteNova=%s "
                        + "pedidosSemCobranca=%s,%s expirado=%s", aberta, naoCriadaK3, naoCriadaK5,
                        pendenteNova, semCobrancaVencido, semCobranca, expirado)
                .isEmpty();
        assertThat(semAPendenteVelha)
                .as("E2, gêmeo por presença — a pendente antiga (%s) sai em TODA forma que "
                        + "responde 2xx; estas formas responderam 2xx sem ela", pendenteVelha)
                .isEmpty();
    }

    

    @Test
    void e3_aDecisaoK5DeixaRastroELiberaUmaTentativaNova() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        UUID cobranca = idDe(ultimaTentativaDe(orderId));
        envelhecer(cobranca, VELHA);
        Admin operador = novoAdmin();

        Resposta decisao = decidir(operador.token(), cobranca, "NOT_CREATED");
        Map<String, Object> decidida = tentativa(cobranca);
        porta.reiniciar();
        Resposta retomada = postarCheckout(cliente);
        List<Map<String, Object>> tentativas = tentativasDe(orderId);

        assertThat(decisao.doisXX())
                .as("E3 — POST %s com outcome NOT_CREATED, como ADMIN, sobre pendente há mais de "
                        + "X: a decisão vence. Veio: %s", ROTA_DA_DECISAO, decisao)
                .isTrue();
        assertThat(decidida.get("status"))
                .as("E3 / K5 — a cobrança vai a NOT_CREATED")
                .isEqualTo("NOT_CREATED");
        assertThat(decidida.get("reviewed_by"))
                .as("E3 — reviewed_by = o ADMIN que chamou")
                .isEqualTo(operador.id());
        assertThat(decidida.get("reviewed_at")).as("E3 — reviewed_at preenchido").isNotNull();
        assertThat(decidida.get("review_outcome"))
                .as("E3 — review_outcome = 'NOT_CREATED' (o estado NO INSTANTE da decisão, §3.6)")
                .isEqualTo("NOT_CREATED");
        assertThat(porta.chamadasPara(orderId))
                .as("E3 — depois de K5, o POST seguinte do dono chama a porta UMA vez: %s",
                        retomada)
                .hasSize(1);
        assertThat(tentativas)
                .as("E3 — e cria LINHA NOVA; a decidida continua lá")
                .hasSize(2);
        assertThat(tentativa(cobranca))
                .as("E3 — a linha decidida continua INTACTA depois da retomada (o rastro é "
                        + "imutável por construção, §3.1)")
                .isEqualTo(decidida);
    }

    

    

    @Test
    void e4_outcomeOpenEhRecusado() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        UUID cobranca = idDe(ultimaTentativaDe(orderId));
        envelhecer(cobranca, VELHA);
        Map<String, Object> antes = tentativa(cobranca);

        Resposta r = decidir(novoAdmin().token(), cobranca, "OPEN");

        assertThat(r.status())
                .as("E4 — outcome OPEN é recusado (K6 é gatilho C7: sem artefato a gravar, OPEN "
                        + "seria link que não existe). 400 ou 422; 404 aqui é rota que não "
                        + "existe. Veio: %s", r)
                .isIn(400, 422);
        assertThat(tentativa(cobranca)).as("E4 — a cobrança não muda").isEqualTo(antes);
    }

    @Test
    void e4_pendenteMaisNovaQueXEhRecusadaCom422() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        UUID nova = idDe(ultimaTentativaDe(orderId));
        Map<String, Object> antes = tentativa(nova);

        Resposta r = decidir(novoAdmin().token(), nova, "NOT_CREATED");

        assertThat(r.status())
                .as("E4 — pendente MAIS NOVA que X: a chamada pode estar em voo, e decidir é a "
                        + "corrida de §2. 422. Veio: %s", r)
                .isEqualTo(422);
        assertThat(r.code()).as("sem código novo (§3.4)").isEqualTo("DOMAIN_RULE_VIOLATION");
        assertThat(tentativa(nova)).as("E4 — a cobrança não muda").isEqualTo(antes);
    }

    


    @Test
    void e4_cobrancaQueJaSaiuDePendenteEhRecusadaEORastroNaoMuda() throws Exception {
        Admin primeiro = novoAdmin();
        Admin segundo = novoAdmin();

        Resposta criada = postarCheckout(clienteComCarrinho("70.00"));
        assertThat(criada.status()).as("guarda: %s", criada).isEqualTo(201);
        UUID aberta = idDe(ultimaTentativaDe(UUID.fromString(criada.id())));
        envelhecer(aberta, VELHA);

        UUID naoCriadaK3 = idDe(ultimaTentativaDe(
                pedidoDepoisDeFalha(clienteComCarrinho("70.00"), Modo.FALHA_CERTA)));
        envelhecer(naoCriadaK3, VELHA);

        UUID decidida = idDe(ultimaTentativaDe(
                pedidoDepoisDeFalha(clienteComCarrinho("70.00"), Modo.FALHA_CRUA)));
        envelhecer(decidida, VELHA);
        decidirComoPassoDoRoteiro(primeiro, decidida);

        Map<UUID, Map<String, Object>> antes = new LinkedHashMap<>();
        for (UUID id : List.of(aberta, naoCriadaK3, decidida)) antes.put(id, tentativa(id));

        Map<UUID, Resposta> respostas = new LinkedHashMap<>();
        for (UUID id : antes.keySet()) respostas.put(id, decidir(segundo.token(), id, "NOT_CREATED"));

        for (UUID id : antes.keySet()) {
            Resposta r = respostas.get(id);
            assertThat(r.status())
                    .as("E4 — decidir sobre cobrança que não está PENDING_CONFIRMATION (%s, %s): "
                            + "422. Veio: %s", id, antes.get(id).get("status"), r)
                    .isEqualTo(422);
            assertThat(r.code()).isEqualTo("DOMAIN_RULE_VIOLATION");
            assertThat(tentativa(id))
                    .as("E4 — a cobrança %s não muda — e o rastro da decisão anterior, quando há, "
                            + "continua o do PRIMEIRO operador", id)
                    .isEqualTo(antes.get(id));
        }
    }

    

    







    @Test
    void e5_duasDecisoesConcorrentesSoUmaVence() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        UUID cobranca = idDe(ultimaTentativaDe(orderId));
        envelhecer(cobranca, VELHA);
        Admin a = novoAdmin();
        Admin b = novoAdmin();

        Connection trava = travarTentativa(cobranca);
        Future<Resposta> fa;
        Future<Resposta> fb;
        boolean asDuasEsperaram;
        try {
            fa = emParalelo(() -> decidir(a.token(), cobranca, "NOT_CREATED"));
            fb = emParalelo(() -> decidir(b.token(), cobranca, "NOT_CREATED"));
            aguardar(() -> sessoesEsperandoLockEmPayments("") >= 2 || fa.isDone() && fb.isDone(),
                    TETO_DE_ESPERA, "as duas decisões pararem atrás da trava (ou terminarem)");
            asDuasEsperaram = sessoesEsperandoLockEmPayments("") >= 2;
        } finally {
            fechar(trava, true);
        }
        Resposta ra = colher(fa, "a decisão de A");
        Resposta rb = colher(fb, "a decisão de B");
        Map<String, Object> depois = tentativa(cobranca);

        assertThat(List.of(ra, rb).stream().filter(Resposta::doisXX).count())
                .as("E5 — EXATAMENTE UMA decisão vence. Respostas: %s | %s", ra, rb)
                .isEqualTo(1);
        Resposta perdedora = ra.doisXX() ? rb : ra;
        Admin vencedor = ra.doisXX() ? a : b;
        assertThat(perdedora.status()).as("E5 — a outra recebe 422: %s", perdedora).isEqualTo(422);
        assertThat(perdedora.code()).isEqualTo("DOMAIN_RULE_VIOLATION");
        assertThat(depois.get("reviewed_by"))
                .as("E5 — o rastro é o da vencedora, escrito uma vez")
                .isEqualTo(vencedor.id());
        assertThat(asDuasEsperaram)
                .as("E5, GÊMEO — as duas decisões chegaram à escrita com a trava segurando: a "
                        + "intercalação aconteceu, não uma serialização")
                .isTrue();
    }

    

    





    @Test
    void e6_autorizacaoDasDuasRotas() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID cobranca = idDe(ultimaTentativaDe(pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA)));
        envelhecer(cobranca, VELHA);
        String vendedora = tokenDe(Role.SELLER);
        String clienteToken = tokenDe(Role.CLIENT);
        Admin operador = novoAdmin();

        Map<String, Integer> visto = new LinkedHashMap<>();
        visto.put("GET  fila    SELLER", lerFila(vendedora, null).status());
        visto.put("GET  fila    CLIENT", lerFila(clienteToken, null).status());
        visto.put("GET  fila    sem token", lerFila(null, null).status());
        visto.put("POST decisão SELLER", decidir(vendedora, cobranca, "NOT_CREATED").status());
        visto.put("POST decisão CLIENT", decidir(clienteToken, cobranca, "NOT_CREATED").status());
        visto.put("POST decisão sem token", decidir(null, cobranca, "NOT_CREATED").status());
        visto.put("GET  fila    ADMIN", lerFila(operador.token(), null).status());
        visto.put("POST decisão ADMIN", decidir(operador.token(), cobranca, "NOT_CREATED").status());

        Map<String, Integer> esperado = new LinkedHashMap<>();
        esperado.put("GET  fila    SELLER", 403);
        esperado.put("GET  fila    CLIENT", 403);
        esperado.put("GET  fila    sem token", 401);
        esperado.put("POST decisão SELLER", 403);
        esperado.put("POST decisão CLIENT", 403);
        esperado.put("POST decisão sem token", 401);
        esperado.put("GET  fila    ADMIN", 200);
        esperado.put("POST decisão ADMIN", visto.get("POST decisão ADMIN") >= 200
                && visto.get("POST decisão ADMIN") < 300 ? visto.get("POST decisão ADMIN") : 200);

        assertThat(visto)
                .as("E6 — /admin/** é do ADMIN (ADR 0016 §3.4, Q1). A vendedora NÃO enxerga a "
                        + "fila em tela nenhuma (item 4.1): 403, e não 404. ADMIN passa (2xx)")
                .containsExactlyEntriesOf(esperado);
        assertThat(tentativa(cobranca).get("reviewed_by"))
                .as("E6 — a decisão que passou foi a do ADMIN; as recusadas não escreveram")
                .isEqualTo(operador.id());
    }

    

    

    private static List<Object> itensDe(Resposta r) {
        Object raiz = ler(r.corpo(), "$");
        if (raiz instanceof List<?> lista) return new ArrayList<>(lista);
        if (raiz instanceof Map<?, ?> mapa) {
            for (String chave : List.of("content", "items", "data", "results")) {
                if (mapa.get(chave) instanceof List<?> lista) return new ArrayList<>(lista);
            }
            for (Object v : mapa.values()) {
                if (v instanceof List<?> lista) return new ArrayList<>(lista);
            }
        }
        return List.of();
    }

    private static List<Object> folhas(Object no) {
        List<Object> saida = new ArrayList<>();
        if (no instanceof Map<?, ?> m) m.values().forEach(v -> saida.addAll(folhas(v)));
        else if (no instanceof List<?> l) l.forEach(v -> saida.addAll(folhas(v)));
        else if (no != null) saida.add(no);
        return saida;
    }

    private static List<String> textos(List<Object> folhas) {
        return folhas.stream().map(String::valueOf).toList();
    }

    private static List<Object> folhasDoItemQueCita(List<Object> itens, UUID id) {
        for (Object item : itens) {
            List<Object> f = folhas(item);
            if (textos(f).contains(id.toString())) return f;
        }
        return List.of();
    }

    

    private String porQueNaoEhPendenteAntiga(Object item) {
        for (String t : textos(folhas(item))) {
            UUID candidato;
            try {
                candidato = UUID.fromString(t);
            } catch (IllegalArgumentException naoEhUuid) {
                continue;
            }
            List<Map<String, Object>> linha = jdbc.queryForList(
                    "SELECT * FROM payments WHERE id = ?", candidato);
            if (linha.isEmpty()) continue;
            Map<String, Object> l = linha.get(0);
            Instant criada = ((Timestamp) l.get("created_at")).toInstant();
            boolean antiga = criada.isBefore(clock.instant().minus(X));
            if (!"PENDING_CONFIRMATION".equals(l.get("status"))) {
                return "cobrança " + candidato + " está " + l.get("status");
            }
            if (!antiga) return "cobrança " + candidato + " é mais nova que X (" + criada + ")";
            return null;
        }
        return "item não cita nenhuma cobrança de payments";
    }

    private static BigDecimal comoDinheiro(Object folha) {
        if (folha instanceof Number n) return new BigDecimal(n.toString());
        try {
            return new BigDecimal(String.valueOf(folha));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Instant comoInstante(Object folha) {
        String s = String.valueOf(folha);
        try {
            return Instant.parse(s).truncatedTo(ChronoUnit.MILLIS);
        } catch (RuntimeException e) {
            try {
                return OffsetDateTime.parse(s).toInstant().truncatedTo(ChronoUnit.MILLIS);
            } catch (RuntimeException e2) {
                return null;
            }
        }
    }

    

    private UUID pedidoPorSql(String status, Duration prazo) {
        String m = UUID.randomUUID().toString().substring(0, 8);
        User u = userRepository.save(User.createWithRole("Sem cobrança " + m,
                "sem-cobranca-" + m + "@teste.local", "hash", Role.CLIENT));
        UUID id = UUID.randomUUID();
        Timestamp agora = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO orders (id, user_id, status, subtotal, shipping_cost,
                                    ship_recipient_name, ship_phone, ship_zip_code,
                                    ship_street, ship_number, ship_complement,
                                    ship_district, ship_city, ship_uf,
                                    expires_at, created_at, updated_at)
                VALUES (?, ?, ?, 50.00, 19.90, 'Maria', '11999998888', '01310100',
                        'Av. Paulista', '1000', NULL, 'Bela Vista', 'São Paulo', 'SP', ?, ?, ?)
                """, id, u.getId(), status, Timestamp.from(clock.instant().plus(prazo)),
                Timestamp.from(clock.instant().minus(VELHA)), agora);
        return id;
    }

    













































































































}
