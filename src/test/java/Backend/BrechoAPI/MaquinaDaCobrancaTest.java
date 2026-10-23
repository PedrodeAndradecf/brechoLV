package Backend.BrechoAPI;

import Backend.BrechoAPI.PortaDeCobrancaControlavel.Chamada;
import Backend.BrechoAPI.PortaDeCobrancaControlavel.Modo;
import Backend.BrechoAPI.service.PaymentGateway;
import Backend.BrechoAPI.service.PaymentGateway.PixCharge;
import Backend.BrechoAPI.service.impl.FakePixPaymentGateway;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

















































































































@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PortaDeCobrancaControlavel.Config.class})
@TestPropertySource(properties = "checkout.order-ttl=PT30M")
@ExtendWith(OutputCaptureExtension.class)
class MaquinaDaCobrancaTest extends ApoioDeCobrancaHttp {

    @Autowired PaymentGateway portaInjetadaNoSistema;

    

    





    @Test
    void andaime_aPortaDesteContextoEhAControlavel() throws Exception {
        assertThat(portaInjetadaNoSistema)
                .as("o PaymentGateway injetado no sistema tem de ser a porta deste contexto")
                .isSameAs(porta);

        UUID orderId = UUID.randomUUID();
        var prazo = clock.instant().plusSeconds(1800);
        assertThat(porta.createPixCharge(orderId, BigDecimal.TEN, prazo))
                .as("NORMAL devolve o artefato do adaptador de produção, byte a byte")
                .isEqualTo(new FakePixPaymentGateway().createPixCharge(orderId, BigDecimal.TEN, prazo));

        porta.modo(Modo.DISTINTO);
        PixCharge a = porta.createPixCharge(orderId, BigDecimal.TEN, prazo);
        PixCharge b = porta.createPixCharge(orderId, BigDecimal.TEN, prazo);
        assertThat(a.copyPaste())
                .as("DISTINTO: duas chamadas para o MESMO pedido devolvem artefatos DIFERENTES — "
                        + "é o que torna o lost update visível (B5)")
                .isNotEqualTo(b.copyPaste());
        assertThat(a.txid()).isNotEqualTo(b.txid());

        porta.reiniciar();
        porta.armar(Modo.NORMAL, k -> k == 1);
        Future<PixCharge> segurada = emParalelo(() -> porta.createPixCharge(orderId, BigDecimal.TEN, prazo));
        aguardar(() -> porta.segurandoAgora() == 1, TETO_DE_ESPERA, "a porta segurar a chamada 1");
        PixCharge livre = porta.createPixCharge(orderId, BigDecimal.TEN, prazo);
        assertThat(segurada.isDone())
                .as("a chamada 1 continua segurada enquanto a 2 passa livre")
                .isFalse();
        assertThat(livre).isNotNull();
        porta.liberar();
        assertThat(colher(segurada, "a chamada segurada")).isNotNull();
        assertThat(porta.segurandoAgora()).isZero();
        assertThat(porta.chamadas()).extracting(Chamada::numero).containsExactly(1, 2);
    }

    

    









    @Test
    void b1_aCobrancaPendenteJaEstaCommitadaQuandoAPortaEhChamada() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        porta.armar(Modo.NORMAL, k -> k == 1);

        Future<Resposta> checkout = emParalelo(() -> postarCheckout(cliente));
        aguardar(() -> porta.segurandoAgora() == 1 || checkout.isDone(), TETO_DE_ESPERA,
                "o checkout chegar à porta");
        assertThat(porta.segurandoAgora())
                .as("guarda: a chamada à porta está EM VOO. Sem isto, a leitura abaixo não é "
                        + "'durante a chamada'. Resposta (se já veio): %s",
                        checkout.isDone() ? checkout.get() : "—")
                .isEqualTo(1);

        UUID orderId = porta.chamadas().get(0).orderId();
        List<Map<String, Object>> duranteAChamada = tentativasDe(orderId);
        porta.liberar();
        Resposta resposta = colher(checkout, "o checkout");

        assertThat(duranteAChamada)
                .as("B1 — durante a chamada há exatamente UMA tentativa commitada")
                .hasSize(1);
        Map<String, Object> linha = duranteAChamada.get(0);
        assertThat(linha.get("status"))
                .as("B1 / K1 — a tentativa já existe, commitada, como PENDING_CONFIRMATION, "
                        + "ANTES da chamada (ADR 0016 §2). Sem isto, uma queda durante a chamada "
                        + "não deixa registro de que se tentou cobrar")
                .isEqualTo("PENDING_CONFIRMATION");
        assertThat((BigDecimal) linha.get("amount"))
                .as("B1 / K1 — amount gravado antes da chamada = total do pedido")
                .isNotNull()
                .isEqualByComparingTo(totalDoPedido(orderId));
        assertThat(linha.get("order_nsu"))
                .as("B1 / K1 — order_nsu gravado antes da chamada")
                .isNotNull();
        assertThat(resposta.status()).as("e o checkout termina 201: %s", resposta).isEqualTo(201);
    }

    

    @Test
    void b2_caminhoFelizDeixaUmaCobrancaAbertaIgualAResposta() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");

        Resposta resposta = postarCheckout(cliente);

        assertThat(resposta.status()).as("guarda: %s", resposta).isEqualTo(201);
        UUID orderId = UUID.fromString(resposta.id());
        List<Map<String, Object>> tentativas = tentativasDe(orderId);
        assertThat(tentativas).as("B2 — uma tentativa só").hasSize(1);
        Map<String, Object> linha = tentativas.get(0);
        assertThat(linha.get("status"))
                .as("B2 / K2 — a cobrança que recebeu o artefato está OPEN")
                .isEqualTo("OPEN");
        assertThat((BigDecimal) linha.get("amount")).isNotNull()
                .isEqualByComparingTo(totalDoPedido(orderId));
        assertThat(linha.get("order_nsu")).isNotNull();
        assertThat(linha.get("pix_copy_paste")).as("B2 — com artefato").isNotNull();
        assertThat(resposta.pixCopyPaste())
                .as("B2 — o pix da resposta é o da LINHA")
                .isEqualTo(linha.get("pix_copy_paste"));
        assertThat(resposta.pixTxid()).isEqualTo(linha.get("txid"));
        assertThat(porta.chamadasPara(orderId)).hasSize(1);
    }

    





    @Test
    void b3_oPerdedorDeK1Recebe200SemArtefatoESemChamar() throws Exception {
        Corrida corrida = duasRetomadasComPortaLenta(Modo.NORMAL);

        assertThat(corrida.portaSegurava())
                .as("gêmeo da corrida: a porta segurava a primeira quando a segunda terminou")
                .isTrue();
        assertThat(corrida.segunda().status())
                .as("B3 — o perdedor de K1 recebe 200 (o pedido como está): %s", corrida.segunda())
                .isEqualTo(200);
        assertThat(corrida.segunda().id()).isEqualTo(corrida.orderId().toString());
        assertThat(corrida.segunda().temPix())
                .as("B3 — pix: null — a cobrança viva ainda não tem artefato, e o perdedor NÃO "
                        + "tem artefato próprio (ele não chamou). Veio: %s", corrida.segunda())
                .isFalse();
        assertThat(corrida.chamadas())
                .as("B3 — só a primeira chamou a porta")
                .isEqualTo(1);
    }

    












    @Test
    void b4i_outraViolacaoDeUnicidadeNoInsertDeK1Eh500() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        porta.reiniciar();

        String indice = "qa_dv0c_b4i_" + UUID.randomUUID().toString().substring(0, 8);
        Resposta comPlantacao;
        int chamadasComPlantacao;
        jdbc.execute("CREATE UNIQUE INDEX " + indice + " ON payments (order_id) WHERE order_id = '"
                + orderId + "'");
        try {
            comPlantacao = postarCheckout(cliente);
            chamadasComPlantacao = porta.chamadasPara(orderId).size();
        } finally {
            jdbc.execute("DROP INDEX IF EXISTS " + indice);
        }

        porta.reiniciar();
        Resposta semPlantacao = postarCheckout(cliente);

        assertThat(comPlantacao.status())
                .as("B4 (i) — 23505 de OUTRA constraint no insert de K1 é erro NOSSO: 500 "
                        + "PERSISTENCE_FAILURE. 200 aqui é falha de infraestrutura virando 'há "
                        + "cobrança pendente' — e saindo como SUCESSO (ADR 0016 §2, Parte A item "
                        + "8). Veio: %s", comPlantacao)
                .isEqualTo(500);
        assertThat(comPlantacao.code()).isEqualTo("PERSISTENCE_FAILURE");
        assertThat(chamadasComPlantacao)
                .as("B4 (i) — e zero chamadas à porta: K1 não aconteceu")
                .isZero();
        assertThat(semPlantacao.status())
                .as("B4, gêmeo — sem a plantação, o mesmo POST volta ao caminho feliz: %s",
                        semPlantacao)
                .isEqualTo(200);
        assertThat(semPlantacao.temPix()).isTrue();
        assertThat(porta.chamadasPara(orderId)).hasSize(1);
    }

    


    @Test
    void b4ii_violacaoDeCheckNoInsertDeK1Eh500() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        porta.reiniciar();

        String check = "qa_dv0c_b4ii_" + UUID.randomUUID().toString().substring(0, 8);
        Resposta comPlantacao;
        int chamadasComPlantacao;
        jdbc.execute("ALTER TABLE payments ADD CONSTRAINT " + check + " CHECK (order_id <> '"
                + orderId + "') NOT VALID");
        try {
            comPlantacao = postarCheckout(cliente);
            chamadasComPlantacao = porta.chamadasPara(orderId).size();
        } finally {
            jdbc.execute("ALTER TABLE payments DROP CONSTRAINT IF EXISTS " + check);
        }

        porta.reiniciar();
        Resposta semPlantacao = postarCheckout(cliente);

        assertThat(chamadasComPlantacao)
                .as("B4 (ii) — zero chamadas à porta: o insert de K1 foi recusado (23514), e "
                        + "sem K1 não se chama")
                .isZero();
        assertThat(comPlantacao.status())
                .as("B4 (ii) — violação de CHECK no insert de K1: 500, nunca 200. Veio: %s",
                        comPlantacao)
                .isEqualTo(500);
        assertThat(comPlantacao.code()).isEqualTo("PERSISTENCE_FAILURE");
        assertThat(semPlantacao.status())
                .as("B4, gêmeo — sem a plantação, o caminho feliz: %s", semPlantacao)
                .isEqualTo(200);
        assertThat(semPlantacao.temPix()).isTrue();
    }

    



























    @Test
    void b5_todoArtefatoEm2xxEstaEmPayments_retomadasConcorrentes() throws Exception {
        Disputa disputa = retomadasConcorrentesComEscritaForcada(Modo.DISTINTO);

        assertThat(disputa.entraramNaPorta())
                .as("guarda: ao menos uma retomada chamou a porta")
                .isGreaterThanOrEqualTo(1);
        assertThat(artefatosEm2xxForaDePayments(disputa.orderId(), disputa.respostas()))
                .as("B5 — NENHUMA resposta 2xx pode carregar artefato que não esteja em "
                        + "payments: o cliente copia um link que o sistema não guardou, e que "
                        + "continua pagável lá fora (ADR 0016 §2, 'o que o perdedor de K2 "
                        + "devolve'). Respostas: %s | guardados: %s", disputa.respostas(),
                        artefatosGuardadosDe(disputa.orderId()))
                .isEmpty();
    }

    








    @Test
    void b5_gemeoDeterministico_oFalsoDeHojeFicaVerdeMesmoSabotado() throws Exception {
        Disputa disputa = retomadasConcorrentesComEscritaForcada(Modo.NORMAL);

        assertThat(artefatosEm2xxForaDePayments(disputa.orderId(), disputa.respostas()))
                .as("gêmeo rotulado — verde com ou sem lost update; NÃO é prova de B5")
                .isEmpty();
    }

    












    @Test
    void b6_k2CondicionalPerdeParaADecisaoHumanaENaoSobrescreveORastro(CapturedOutput saida)
            throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        Admin operador = novoAdmin();
        porta.armar(Modo.DISTINTO, k -> k == 1);

        Future<Resposta> checkout = emParalelo(() -> postarCheckout(cliente));
        aguardar(() -> porta.segurandoAgora() == 1 || checkout.isDone(), TETO_DE_ESPERA,
                "o checkout chegar à porta");
        assertThat(porta.segurandoAgora()).as("guarda: a chamada está em voo").isEqualTo(1);

        UUID orderId = porta.chamadas().get(0).orderId();
        UUID cobranca = idDe(ultimaTentativaDe(orderId));
        envelhecer(cobranca, VELHA);
        decidirComoPassoDoRoteiro(operador, cobranca);
        Map<String, Object> decidida = tentativa(cobranca);

        porta.liberar();
        Resposta resposta = colher(checkout, "o checkout");
        PixCharge devolvidoPelaPorta = porta.chamadas().get(0).artefato();
        Map<String, Object> depois = tentativa(cobranca);
        Resposta leitura = lerPedido(cliente, orderId);

        assertThat(resposta.status())
                .as("B6 — K2 perdeu (a cobrança já não estava PENDING_CONFIRMATION): a resposta "
                        + "é a do desconhecido, 502. Veio: %s", resposta)
                .isEqualTo(502);
        assertThat(resposta.code()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        assertThat(resposta.mensagem())
                .as("B6 — a mensagem do desconhecido: 'em análise', SEM convite a tentar de novo")
                .containsIgnoringCase("em análise")
                .doesNotContainIgnoringCase("de novo");
        assertThat(depois.get("status"))
                .as("B6 — K2 NÃO sobrescreve a decisão humana: a linha continua NOT_CREATED")
                .isEqualTo("NOT_CREATED");
        assertThat(depois.get("pix_copy_paste")).as("B6 — sem artefato").isNull();
        assertThat(depois.get("txid")).isNull();
        assertThat(decidida.get("reviewed_by"))
                .as("guarda: o rastro de K5 existia antes de a porta devolver")
                .isEqualTo(operador.id());
        assertThat(List.of(depois.get("reviewed_by"), depois.get("reviewed_at"),
                depois.get("review_outcome")))
                .as("B6 — o rastro de K5 intacto, byte a byte")
                .isEqualTo(List.of(decidida.get("reviewed_by"), decidida.get("reviewed_at"),
                        decidida.get("review_outcome")));
        assertThat(List.of(resposta, leitura))
                .as("B6 / B5 — o artefato que a porta devolveu (%s) NÃO aparece em resposta 2xx "
                        + "nenhuma", devolvidoPelaPorta.copyPaste())
                .noneMatch(r -> r.doisXX()
                        && devolvidoPelaPorta.copyPaste().equals(r.pixCopyPaste()));
        assertThat(artefatosEm2xxForaDePayments(orderId, List.of(resposta, leitura))).isEmpty();
        assertThat(saida.getAll().lines()
                .filter(l -> l.contains(devolvidoPelaPorta.copyPaste())).toList())
                .as("B6 — o artefato que voltou depois da decisão humana sai em LOG DE ERRO com o "
                        + "order_nsu: é a única cópia dele (ADR 0016 §2)")
                .isNotEmpty()
                .anySatisfy(l -> assertThat(l).contains("ERROR")
                        .contains(String.valueOf(depois.get("order_nsu"))));
    }

    







    @Test
    void b8_cobrancaPendenteSeguraOVencimento() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        vencerPrazoDoPedido(orderId);
        porta.reiniciar();

        Resposta leituraAntes = lerPedido(cliente, orderId);
        Resposta retomada = postarCheckout(cliente);
        Resposta leituraDepois = lerPedido(cliente, orderId);

        assertThat(leituraAntes.pedidoStatus())
                .as("B8 — statusEfetivoDe NÃO projeta EXPIRED sobre pedido cuja cobrança viva "
                        + "está PENDING_CONFIRMATION, mesmo com o prazo vencido")
                .isEqualTo("AWAITING_PAYMENT");
        assertThat(retomada.status())
                .as("B8 — expireDueOf NÃO vence o pedido: 201 aqui é pedido NOVO, com link novo, "
                        + "enquanto o primeiro pode existir lá fora. Veio: %s", retomada)
                .isEqualTo(200);
        assertThat(retomada.id()).isEqualTo(orderId.toString());
        assertThat(retomada.temPix()).as("B8 — pix: null").isFalse();
        assertThat(porta.chamadas()).as("B8 — zero chamadas").isEmpty();
        assertThat(statusDoPedido(orderId)).isEqualTo("AWAITING_PAYMENT");
        assertThat(pedidosDe(cliente)).hasSize(1);
        assertThat(leituraDepois.pedidoStatus()).isEqualTo("AWAITING_PAYMENT");
    }

    

    @Test
    void b8_controle_naoCriadaVencidaVenceComoHoje() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        vencerPrazoDoPedido(orderId);
        porta.reiniciar();

        Resposta novo = postarCheckout(cliente);

        assertThat(novo.status())
                .as("controle B8 — depois de falha certa o pedido vence: o próximo checkout é "
                        + "pedido NOVO (201). Veio: %s", novo)
                .isEqualTo(201);
        assertThat(novo.id()).isNotEqualTo(orderId.toString());
        assertThat(statusDoPedido(orderId)).isEqualTo("EXPIRED");
    }

    

    @Test
    void b8_controle_abertaVencidaEhProjetadaExpired() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        Resposta criado = postarCheckout(cliente);
        assertThat(criado.status()).as("guarda: %s", criado).isEqualTo(201);
        UUID orderId = UUID.fromString(criado.id());
        vencerPrazoDoPedido(orderId);

        assertThat(lerPedido(cliente, orderId).pedidoStatus())
                .as("controle B8 — cobrança OPEN com prazo vencido: o GET projeta EXPIRED")
                .isEqualTo("EXPIRED");
    }

    


    @Test
    void b9_controle_pedidoComCobrancaPendenteContinuaAberto() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        adicionarAoCarrinho(cliente, criarPeca("40.00"));
        porta.reiniciar();

        Resposta outroCarrinho = postarCheckout(cliente);

        assertThat(outroCarrinho.status())
                .as("controle B9 — carrinho diferente com pedido de cobrança pendente: 409. "
                        + "Veio: %s", outroCarrinho)
                .isEqualTo(409);
        assertThat(outroCarrinho.code()).isEqualTo("ORDER_ALREADY_OPEN");
        assertThat(porta.chamadas()).isEmpty();
        assertThat(pedidosDe(cliente)).containsExactly(orderId);
    }

    

    









    @Test
    void c1a_duploCliqueComPortaLentaChamaAPortaUmaVez() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        porta.armar(Modo.NORMAL, k -> k == 1);

        Future<Resposta> primeira = emParalelo(() -> postarCheckout(cliente));
        aguardar(() -> porta.segurandoAgora() == 1 || primeira.isDone(), TETO_DE_ESPERA,
                "o primeiro clique chegar à porta");
        Future<Resposta> segundaF = emParalelo(() -> postarCheckout(cliente));
        Resposta segunda = segundaTerminaEnquantoAPrimeiraEstaSegurada(segundaF);
        boolean segurava = porta.segurandoAgora() == 1;
        porta.liberar();
        Resposta r1 = colher(primeira, "o primeiro clique");

        assertThat(segurava)
                .as("C1, GÊMEO — a porta segurava a primeira chamada quando a segunda requisição "
                        + "TERMINOU. Falso aqui = as duas serializaram e nada abaixo prova corrida")
                .isTrue();
        UUID orderId = oUnicoPedidoDe(cliente);
        assertThat(porta.chamadasPara(orderId))
                .as("C1 (a) — EXATAMENTE UMA chamada à porta por pedido, com porta lenta (ADR "
                        + "0016 §3.10). Duas = dois links pagáveis para o mesmo pedido (SONDA-B). "
                        + "Respostas: %s | %s", r1, segunda)
                .hasSize(1);
        assertThat(vivasDe(orderId)).as("C1 (a) — uma tentativa viva").hasSize(1);
        assertThat(segunda.status())
                .as("C1 (a) — a segunda é o perdedor de K1 (200) ou a recusa da casa a quem perde "
                        + "em createOrder (409): %s", segunda)
                .isIn(200, 409);
        assertThat(segunda.temPix())
                .as("C1 (a) — a segunda resposta não tem artefato próprio: %s", segunda)
                .isFalse();
    }

    

    @Test
    void c1b_duasRetomadasComPortaLentaChamamAPortaUmaVez() throws Exception {
        Corrida corrida = duasRetomadasComPortaLenta(Modo.NORMAL);

        assertThat(corrida.portaSegurava())
                .as("C1, GÊMEO — a porta segurava a primeira quando a segunda terminou")
                .isTrue();
        assertThat(corrida.chamadas())
                .as("C1 (b) — EXATAMENTE UMA chamada à porta: duas retomadas simultâneas, uma "
                        + "chamada (SONDA-A mediu duas). Respostas: %s | %s", corrida.primeira(),
                        corrida.segunda())
                .isEqualTo(1);
        assertThat(vivasDe(corrida.orderId())).as("C1 (b) — uma tentativa viva").hasSize(1);
    }

    
















    @RepeatedTest(5)
    void c2_intercalacaoForcadaNoInsertDeK1() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        exigirV4("plantar, numa transação ABERTA, a tentativa PENDING_CONFIRMATION da "
                + "'primeira requisição'");
        Object nsu = ultimaTentativaDe(orderId).get("order_nsu");
        porta.reiniciar();

        UUID plantada = UUID.randomUUID();
        Connection k1Aberta = transacaoAberta();
        Future<Resposta> checkout;
        boolean esperouNoIndice;
        try {
            try (PreparedStatement ps = k1Aberta.prepareStatement("""
                    INSERT INTO payments (id, order_id, status, amount, order_nsu, created_at, updated_at)
                    VALUES (?, ?, 'PENDING_CONFIRMATION', ?, ?, ?, ?)
                    """)) {
                Timestamp agora = Timestamp.from(clock.instant());
                ps.setObject(1, plantada);
                ps.setObject(2, orderId);
                ps.setBigDecimal(3, totalDoPedido(orderId));
                ps.setObject(4, nsu);
                ps.setTimestamp(5, agora);
                ps.setTimestamp(6, agora);
                ps.executeUpdate();
            }
            checkout = emParalelo(() -> postarCheckout(cliente));
            aguardar(() -> sessoesEsperandoLockEmPayments("insert") >= 1 || checkout.isDone()
                            || porta.entradas() >= 1, TETO_DE_ESPERA,
                    "o insert de K1 do dono esperar a transação aberta (ou o checkout terminar)");
            esperouNoIndice = sessoesEsperandoLockEmPayments("insert") >= 1;
        } finally {
            fechar(k1Aberta, true);
        }
        Resposta resposta = colher(checkout, "o checkout");

        assertThat(porta.chamadasPara(orderId))
                .as("C2 — ZERO chamadas: a K1 da 'primeira requisição' estava aberta, sem commit, "
                        + "quando o dono tentou a dele. Uma chamada aqui = a guarda é um 'exists' "
                        + "(que não enxerga linha não commitada), não o índice. Resposta: %s",
                        resposta)
                .isEmpty();
        assertThat(resposta.status()).as("C2 — o perdedor de K1: 200. %s", resposta).isEqualTo(200);
        assertThat(resposta.temPix()).isFalse();
        assertThat(vivasDe(orderId)).extracting(ApoioDeCobrancaHttp::idDe)
                .as("C2 — a única viva é a da 'primeira requisição'")
                .containsExactly(plantada);
        assertThat(esperouNoIndice)
                .as("C2, GÊMEO — o insert do dono ESPEROU a transação aberta: foi a intercalação, "
                        + "e não uma leitura depois do commit, que decidiu")
                .isTrue();
    }

    








    @Test
    void c3_cobrancaPendentePlantadaMaisPostNaoChamaAPorta() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        exigirV4("plantar a tentativa PENDING_CONFIRMATION da queda entre K1 e K2");
        UUID plantada = UUID.randomUUID();
        Timestamp agora = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO payments (id, order_id, status, amount, order_nsu, created_at, updated_at)
                VALUES (?, ?, 'PENDING_CONFIRMATION', ?, ?, ?, ?)
                """, plantada, orderId, totalDoPedido(orderId),
                ultimaTentativaDe(orderId).get("order_nsu"), agora, agora);
        porta.reiniciar();

        Resposta resposta = postarCheckout(cliente);

        assertThat(porta.chamadas())
                .as("C3 / D1-queda — cobrança pendente sem chamada em voo: o POST do dono NÃO "
                        + "chama (é K4: ninguém escreve). Controle, não prova da corrida")
                .isEmpty();
        assertThat(resposta.status()).as("%s", resposta).isEqualTo(200);
        assertThat(resposta.temPix()).isFalse();
        assertThat(tentativa(plantada).get("status"))
                .as("D1-queda — a tentativa plantada continua PENDING_CONFIRMATION")
                .isEqualTo("PENDING_CONFIRMATION");
    }

    

    


    @Test
    void d1_sucesso() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");

        Resposta primeira = postarCheckout(cliente);
        UUID orderId = UUID.fromString(primeira.id());
        List<Map<String, Object>> tentativas = tentativasDe(orderId);
        porta.reiniciar();
        Resposta seguinte = postarCheckout(cliente);

        assertThat(primeira.status()).isEqualTo(201);
        assertThat(tentativas).hasSize(1);
        assertThat(tentativas.get(0).get("status"))
                .as("D1 sucesso — uma tentativa, OPEN")
                .isEqualTo("OPEN");
        assertThat(primeira.pixCopyPaste()).isEqualTo(tentativas.get(0).get("pix_copy_paste"));
        assertThat(porta.chamadas()).as("D1 sucesso — o POST seguinte não chama").isEmpty();
        assertThat(seguinte.pixCopyPaste()).isEqualTo(primeira.pixCopyPaste());
    }

    






    @Test
    void d1_falhaCerta() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        porta.modo(Modo.FALHA_CERTA);

        Resposta falha = postarCheckout(cliente);
        UUID orderId = oUnicoPedidoDe(cliente);
        List<Map<String, Object>> depoisDaFalha = tentativasDe(orderId);
        porta.reiniciar();
        Resposta seguinte = postarCheckout(cliente);
        List<Map<String, Object>> depoisDaRetomada = tentativasDe(orderId);

        assertThat(falha.status()).isEqualTo(502);
        assertThat(falha.code()).isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        assertThat(falha.mensagem())
                .as("D1 falha certa — a mensagem CONVIDA a tentar de novo: é verdade aqui")
                .containsIgnoringCase("de novo");
        assertThat(falha.corpo()).doesNotContain(PortaDeCobrancaControlavel.SEGREDO_DA_CAUSA);
        assertThat(depoisDaFalha).hasSize(1);
        assertThat(depoisDaFalha.get(0).get("status"))
                .as("D1 falha certa / K3 — a tentativa vai a NOT_CREATED")
                .isEqualTo("NOT_CREATED");
        assertThat(depoisDaFalha.get(0).get("pix_copy_paste")).isNull();
        assertThat(depoisDaFalha.get(0).get("review_outcome"))
                .as("D1 falha certa — K3 não escreve rastro: review_outcome nulo (é o que separa "
                        + "o NOT_CREATED que SABE do que DECIDIU, ADR 0016 §3.6)")
                .isNull();
        assertThat(porta.chamadasPara(orderId))
                .as("D1 falha certa — o POST seguinte CHAMA uma vez")
                .hasSize(1);
        assertThat(depoisDaRetomada).extracting(l -> l.get("status"))
                .as("D1 falha certa — LINHA NOVA: a NOT_CREATED fica, e nasce uma OPEN")
                .containsExactly("NOT_CREATED", "OPEN");
        assertThat(seguinte.pixCopyPaste()).isEqualTo(depoisDaRetomada.get(1).get("pix_copy_paste"));
    }

    







    @Test
    void d1_desconhecidoNaoClassificado() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        porta.modo(Modo.FALHA_CRUA);

        Resposta falha = postarCheckout(cliente);
        UUID orderId = oUnicoPedidoDe(cliente);
        Map<String, Object> tentativa = ultimaTentativaDe(orderId);
        porta.reiniciar();
        Resposta seguinte = postarCheckout(cliente);

        assertDesconhecido(falha, tentativa, seguinte, orderId);
    }

    


    @Test
    void d1_desconhecidoComPortaLenta() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        porta.armar(Modo.FALHA_CRUA, k -> k == 1);

        Future<Resposta> checkout = emParalelo(() -> postarCheckout(cliente));
        aguardar(() -> porta.segurandoAgora() == 1 || checkout.isDone(), TETO_DE_ESPERA,
                "o checkout chegar à porta");
        assertThat(porta.segurandoAgora()).as("guarda: a chamada está em voo").isEqualTo(1);
        UUID orderId = porta.chamadas().get(0).orderId();
        Map<String, Object> duranteADemora = ultimaTentativaDe(orderId);
        porta.liberar();
        Resposta falha = colher(checkout, "o checkout");
        Map<String, Object> tentativa = ultimaTentativaDe(orderId);
        porta.reiniciar();
        Resposta seguinte = postarCheckout(cliente);

        assertThat(duranteADemora.get("status"))
                .as("D1 desconhecido lento — durante a demora a tentativa já é "
                        + "PENDING_CONFIRMATION (K1 antes da chamada)")
                .isEqualTo("PENDING_CONFIRMATION");
        assertDesconhecido(falha, tentativa, seguinte, orderId);
    }

    private void assertDesconhecido(Resposta falha, Map<String, Object> tentativa,
                                    Resposta seguinte, UUID orderId) {
        assertThat(falha.status()).as("%s", falha).isEqualTo(502);
        assertThat(falha.code())
                .as("D1 — mesmo status e mesmo $.code nas duas falhas: sem código novo (0015 D-3)")
                .isEqualTo("PAYMENT_GATEWAY_UNAVAILABLE");
        assertThat(falha.mensagem())
                .as("D1 desconhecido — a mensagem diz que o pedido está EM ANÁLISE e NÃO convida "
                        + "a tentar de novo (0015 D-3): o convite é falso aqui, porque o próximo "
                        + "POST não vai chamar. Nem a do adaptador, nem o texto-padrão do handler")
                .containsIgnoringCase("em análise")
                .doesNotContainIgnoringCase("de novo");
        assertThat(falha.corpo()).doesNotContain(PortaDeCobrancaControlavel.SEGREDO_DA_CAUSA);
        assertThat(tentativa.get("status"))
                .as("D1 desconhecido / K4 — a tentativa FICA PENDING_CONFIRMATION: ninguém escreve")
                .isEqualTo("PENDING_CONFIRMATION");
        assertThat(tentativa.get("pix_copy_paste")).isNull();
        assertThat(porta.chamadasPara(orderId))
                .as("D1 desconhecido — o POST seguinte NÃO chama: retomar o desconhecido é o "
                        + "retry automático que a 0015 D-3 proíbe")
                .isEmpty();
        assertThat(seguinte.status()).as("%s", seguinte).isEqualTo(200);
        assertThat(seguinte.temPix()).isFalse();
        assertThat(statusDoPedido(orderId)).isEqualTo("AWAITING_PAYMENT");
    }

    

    










    @Test
    void f1_retentativaDepoisDeK3RepeteOsArgumentosDaPorta() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        Chamada primeira = porta.chamadasPara(orderId).get(0);
        porta.reiniciar();

        Resposta retentativa = postarCheckout(cliente);
        List<Chamada> segundas = porta.chamadasPara(orderId);
        List<Map<String, Object>> tentativas = tentativasDe(orderId);

        assertThat(segundas).as("guarda: a retentativa chamou a porta: %s", retentativa).hasSize(1);
        assertArgumentosIguais(primeira, segundas.get(0));
        assertLinhaNovaRepeteAAnterior(tentativas);
    }

    
    @Test
    void f1_retentativaDepoisDeK5RepeteOsArgumentosDaPorta() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        Admin operador = novoAdmin();
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CRUA);
        Chamada primeira = porta.chamadasPara(orderId).get(0);
        UUID cobranca = idDe(ultimaTentativaDe(orderId));
        envelhecer(cobranca, VELHA);
        decidirComoPassoDoRoteiro(operador, cobranca);
        porta.reiniciar();

        Resposta retentativa = postarCheckout(cliente);
        List<Chamada> segundas = porta.chamadasPara(orderId);
        List<Map<String, Object>> tentativas = tentativasDe(orderId);

        assertThat(segundas).as("guarda: depois de K5, a retentativa chama a porta: %s",
                retentativa).hasSize(1);
        assertArgumentosIguais(primeira, segundas.get(0));
        assertLinhaNovaRepeteAAnterior(tentativas);
    }

    private static void assertArgumentosIguais(Chamada primeira, Chamada segunda) {
        assertThat(segunda.orderId()).isEqualTo(primeira.orderId());
        assertThat(segunda.amount())
                .as("F1 — a retentativa chama a porta com o MESMO amount da primeira: o corpo é "
                        + "congelado na intent (ADR 0016 §3.11). amount derivado do relógio ou do "
                        + "carrinho = segundo link pagável")
                .isEqualByComparingTo(primeira.amount());
        assertThat(segunda.expiresAt())
                .as("F1 — e com o MESMO expiresAt (o Order.expiresAt congelado)")
                .isEqualTo(primeira.expiresAt());
    }

    private static void assertLinhaNovaRepeteAAnterior(List<Map<String, Object>> tentativas) {
        assertThat(tentativas)
                .as("F1 — a retentativa depois de NOT_CREATED é LINHA NOVA (K1 de novo, ADR 0016 "
                        + "§3.1): o pedido tem duas tentativas")
                .hasSize(2);
        Map<String, Object> anterior = tentativas.get(0);
        Map<String, Object> nova = tentativas.get(1);
        assertThat((BigDecimal) nova.get("amount"))
                .as("F1 — a linha nova tem amount, e é o da anterior")
                .isNotNull()
                .isEqualByComparingTo((BigDecimal) anterior.get("amount"));
        assertThat(nova.get("order_nsu"))
                .as("F1 — order_nsu é o mesmo em todas as tentativas de um pedido (§3.11)")
                .isNotNull()
                .isEqualTo(anterior.get("order_nsu"));
    }

    

    record Corrida(UUID orderId, Resposta primeira, Resposta segunda, boolean portaSegurava,
                   int chamadas) {}

    

    private Corrida duasRetomadasComPortaLenta(Modo modo) throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        porta.reiniciar();
        porta.armar(modo, k -> k == 1);

        Future<Resposta> primeira = emParalelo(() -> postarCheckout(cliente));
        aguardar(() -> porta.segurandoAgora() == 1 || primeira.isDone(), TETO_DE_ESPERA,
                "a primeira retomada chegar à porta");
        Future<Resposta> segundaF = emParalelo(() -> postarCheckout(cliente));
        Resposta segunda = segundaTerminaEnquantoAPrimeiraEstaSegurada(segundaF);
        boolean segurava = porta.segurandoAgora() == 1;
        porta.liberar();
        Resposta r1 = colher(primeira, "a primeira retomada");
        return new Corrida(orderId, r1, segunda, segurava, porta.chamadasPara(orderId).size());
    }

    private Resposta segundaTerminaEnquantoAPrimeiraEstaSegurada(Future<Resposta> segunda)
            throws Exception {
        try {
            aguardar(segunda::isDone, TETO_DE_ESPERA,
                    "a segunda requisição terminar enquanto a porta segura a primeira. Se não for "
                            + "a máquina, é o produto SERIALIZANDO a segunda atrás da chamada "
                            + "externa da primeira — o que seria achado (chamada externa fora de "
                            + "transação e de trava)");
        } finally {
            if (!segunda.isDone()) porta.liberar();
        }
        return segunda.get();
    }

    record Disputa(UUID orderId, int entraramNaPorta, List<Resposta> respostas) {}

    

    private Disputa retomadasConcorrentesComEscritaForcada(Modo modo) throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");
        UUID orderId = pedidoDepoisDeFalha(cliente, Modo.FALHA_CERTA);
        porta.reiniciar();
        porta.armar(modo, k -> true);

        Future<Resposta> a = emParalelo(() -> postarCheckout(cliente));
        Future<Resposta> b = emParalelo(() -> postarCheckout(cliente));
        aguardar(() -> porta.entradas() >= 2
                        || (a.isDone() || b.isDone()) && (porta.entradas() >= 1 || a.isDone() && b.isDone()),
                TETO_DE_ESPERA, "as duas retomadas chegarem à porta (ou uma perder antes)");

        int entraram = porta.entradas();
        Connection trava = travarTentativasDe(orderId);
        try {
            porta.liberar();
            aguardar(() -> sessoesEsperandoLockEmPayments("update") >= entraram
                            || a.isDone() && b.isDone(), TETO_DE_ESPERA,
                    "toda chamada devolvida estar com a escrita parada atrás da trava");
        } finally {
            fechar(trava, true);
        }
        Resposta ra = colher(a, "a retomada A");
        Resposta rb = colher(b, "a retomada B");
        Resposta leitura = lerPedido(cliente, orderId);
        return new Disputa(orderId, entraram, List.of(ra, rb, leitura));
    }

    



























































































































































































































































































}
