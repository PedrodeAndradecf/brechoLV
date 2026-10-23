package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.repository.PaymentRepository;
import Backend.BrechoAPI.service.PaymentGateway;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.test.context.TestPropertySource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;























































































@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, PortaDeCobrancaControlavel.Config.class})
@TestPropertySource(properties = "checkout.order-ttl=PT30M")
class TudoQueARespostaDevolveEstaNaLinhaTest extends ApoioDeCobrancaHttp {

    





    private static final Map<String, String> COLUNA_DO_COMPONENTE = Map.of(
            "txid", "txid",
            "copyPaste", "pix_copy_paste");

    

    










    @Test
    void osTresJuntosNaLinhaEARespostaIgualALinha() throws Exception {
        Cliente cliente = clienteComCarrinho("70.00");

        Resposta resposta = postarCheckout(cliente);

        assertThat(resposta.status())
                .as("guarda: o item 1 é sobre o POST BEM-SUCEDIDO — veio %s", resposta)
                .isEqualTo(201);
        UUID orderId = UUID.fromString(resposta.id());
        List<Map<String, Object>> tentativas = tentativasDe(orderId);
        assertThat(tentativas)
                .as("guarda: o caminho feliz deixa exatamente uma tentativa")
                .hasSize(1);
        Map<String, Object> linha = tentativas.get(0);

        
        
        
        assertThat(linha.get("status"))
                .as("DV0-d item 1 — status da tentativa lido do banco: a resposta saiu, logo K2 "
                        + "venceu, logo a linha está OPEN (ADR 0016 §2)")
                .isEqualTo("OPEN");
        assertThat(linha.get("txid"))
                .as("DV0-d item 1 — txid gravado: a referência que casa pagamento com pedido não "
                        + "pode viver só na resposta (é a regra irmã da 2, pendência 11 fechada "
                        + "em 10/10)")
                .isNotNull();
        assertThat(linha.get("pix_copy_paste"))
                .as("DV0-d item 1 — artefato gravado: o que o cliente paga está na linha")
                .isNotNull();

        
        
        assertThat(resposta.pixTxid())
                .as("DV0-d item 1 — o txid da resposta é O DA LINHA")
                .isEqualTo(linha.get("txid"));
        assertThat(resposta.pixCopyPaste())
                .as("DV0-d item 1 — o copyPaste da resposta é O DA LINHA")
                .isEqualTo(linha.get("pix_copy_paste"));

        
        
        
        
        Map<String, Object> pixDaResposta = ler(resposta.corpo(), "$.pix");
        assertThat(pixDaResposta.keySet())
                .as("DV0-d item 1 — NADA vive só na resposta: toda chave de $.pix tem coluna "
                        + "gravada por K2. Chave a mais aqui é artefato que some quando a tela "
                        + "fecha (spec DV0-d, 'o objeto')")
                .containsExactlyInAnyOrderElementsOf(COLUNA_DO_COMPONENTE.keySet());
    }

    

    

















    @Test
    void todoComponenteDePixChargeTemColunaGravadaPorK2() {
        
        
        
        assertThat(colunaExiste("id"))
                .as("gêmeo de presença — a sonda de information_schema sabe dizer SIM")
                .isEqualTo(1);
        assertThat(colunaExiste("qa_dv0d_coluna_que_nao_existe"))
                .as("gêmeo de presença — a sonda sabe dizer NÃO")
                .isEqualTo(0);

        Method k2 = aEscritaQueAbreACobranca();
        List<String> alvosDoSet = alvosDoSet(jpqlDe(k2));

        
        
        
        assertThat(alvosDoSet)
                .as("DV0-d — o SET de K2 (%s) grava o STATUS na mesma instrução que o artefato; "
                        + "sem ele, a linha ganharia artefato e ficaria PENDING_CONFIRMATION "
                        + "(recusado por ck_payments_status_artefato, A2 da DV0-c)",
                        k2.getName())
                .contains("status");

        
        Set<String> colunasGravadasPorK2 = new LinkedHashSet<>();
        for (String alvo : alvosDoSet) {
            String coluna = colunaDoCampo(alvo);
            assertThat(coluna)
                    .as("o alvo '%s' do SET de K2 não mapeia campo nenhum de Payment — a query e "
                            + "a entidade divergiram", alvo)
                    .isNotNull();
            assertThat(colunaExiste(coluna))
                    .as("a coluna '%s' (campo '%s'), gravada por K2, não existe em payments",
                            coluna, alvo)
                    .isEqualTo(1);
            colunasGravadasPorK2.add(coluna);
        }

        
        RecordComponent[] componentes = PaymentGateway.PixCharge.class.getRecordComponents();
        assertThat(componentes)
                .as("guarda anti-vácuo: a porta devolve ao menos um componente")
                .isNotEmpty();
        for (RecordComponent componente : componentes) {
            String coluna = COLUNA_DO_COMPONENTE.get(componente.getName());
            assertThat(coluna)
                    .as("DV0-d item 2 — o componente '%s' de PixCharge NÃO tem coluna declarada "
                            + "no mapa componente × coluna deste teste. Acrescentar componente à "
                            + "porta exige coluna gravada por K2 NA MESMA TRANSAÇÃO (spec §6.1 "
                            + "item 4: o adaptador não é mergeável se devolver campo do fornecedor "
                            + "sem coluna gravada — é a única cópia); a linha do mapa entra no "
                            + "mesmo commit que a coluna e o escritor", componente.getName())
                    .isNotNull();
            assertThat(colunasGravadasPorK2)
                    .as("DV0-d item 2 — o componente '%s' atravessou a porta mas a coluna '%s' "
                            + "NÃO é gravada pelo UPDATE de K2: o valor viveria só na resposta",
                            componente.getName(), coluna)
                    .contains(coluna);
            assertThat(colunaExiste(coluna))
                    .as("DV0-d item 2 — a coluna '%s' do componente '%s' não existe em payments",
                            coluna, componente.getName())
                    .isEqualTo(1);
        }

        
        
        assertThat(COLUNA_DO_COMPONENTE.keySet())
                .as("o mapa componente × coluna e o record PixCharge divergiram: quem sobra de um "
                        + "lado é componente sem coluna (acima) ou entrada morta do mapa")
                .containsExactlyInAnyOrderElementsOf(
                        Arrays.stream(componentes).map(RecordComponent::getName).toList());
    }

    

    









    @Test
    void oArtefatoTemUmEscritorSoNoRepositorio() {
        List<Method> escritoresDoArtefato = Arrays.stream(PaymentRepository.class.getDeclaredMethods())
                .filter(TudoQueARespostaDevolveEstaNaLinhaTest::ehEscritaJpqlDePayment)
                .filter(m -> alvosDoSet(jpqlDe(m)).contains("pixCopyPaste"))
                .sorted(Comparator.comparing(Method::getName))
                .toList();

        assertThat(escritoresDoArtefato)
                .as("DV0-d — K2 é a ÚNICA escrita do artefato (spec, DV0-d-impl). Um segundo "
                        + "UPDATE que atribua pixCopyPaste é K6 chegando (gatilho C7) ou um "
                        + "caminho paralelo que a 0016 §2 não tem — nos dois casos, decisão "
                        + "consciente, não acréscimo calado. Encontrados: %s",
                        escritoresDoArtefato.stream().map(Method::getName).toList())
                .hasSize(1);
        assertThat(escritoresDoArtefato.get(0))
                .as("e esse único escritor é o próprio K2 (o UPDATE que leva a OPEN): artefato e "
                        + "status têm o MESMO escritor, que é o que 'na mesma transação' significa")
                .isEqualTo(aEscritaQueAbreACobranca());
    }

    

    


    private int colunaExiste(String coluna) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = current_schema() AND table_name = 'payments'
                   AND column_name = ?
                """, Integer.class, coluna);
        return n == null ? 0 : n;
    }

    


    private static Method aEscritaQueAbreACobranca() {
        List<Method> candidatas = Arrays.stream(PaymentRepository.class.getDeclaredMethods())
                .filter(TudoQueARespostaDevolveEstaNaLinhaTest::ehEscritaJpqlDePayment)
                .filter(m -> atribuiStatusOpen(jpqlDe(m)))
                .sorted(Comparator.comparing(Method::getName))
                .toList();
        assertThat(candidatas)
                .as("K2 — exatamente UM UPDATE do repositório leva o status a OPEN (ADR 0016 §2: "
                        + "K2 é a transição que chega em OPEN nesta sprint; K6 é gatilho C7). "
                        + "Encontrados: %s",
                        candidatas.stream().map(Method::getName).toList())
                .hasSize(1);
        return candidatas.get(0);
    }

    
    private static boolean ehEscritaJpqlDePayment(Method m) {
        String jpql = jpqlDe(m);
        return m.getAnnotation(Modifying.class) != null && jpql != null
                && jpql.toLowerCase().startsWith("update payment");
    }

    
    private static String jpqlDe(Method m) {
        Query query = m.getAnnotation(Query.class);
        return query == null ? null : query.value().replaceAll("\\s+", " ").trim();
    }

    

    private static List<String> alvosDoSet(String jpql) {
        List<String> alvos = new ArrayList<>();
        for (String atribuicao : clausulaSet(jpql).split(",")) {
            String esquerda = atribuicao.split("=", 2)[0].trim();
            int ponto = esquerda.lastIndexOf('.');
            alvos.add(ponto == -1 ? esquerda : esquerda.substring(ponto + 1));
        }
        return alvos;
    }

    
    private static boolean atribuiStatusOpen(String jpql) {
        for (String atribuicao : clausulaSet(jpql).split(",")) {
            String[] lados = atribuicao.split("=", 2);
            if (lados.length < 2) continue;
            String esquerda = lados[0].trim();
            int ponto = esquerda.lastIndexOf('.');
            String alvo = ponto == -1 ? esquerda : esquerda.substring(ponto + 1);
            if ("status".equals(alvo) && lados[1].contains("OPEN")) return true;
        }
        return false;
    }

    private static String clausulaSet(String jpql) {
        String minusculo = jpql.toLowerCase();
        int set = minusculo.indexOf(" set ");
        int where = minusculo.indexOf(" where ");
        assertThat(set)
                .as("guarda do parser: o JPQL de escrita tem cláusula SET (%s)", jpql)
                .isGreaterThanOrEqualTo(0);
        return where == -1 ? jpql.substring(set + 5) : jpql.substring(set + 5, where);
    }

    



    private static String colunaDoCampo(String propriedade) {
        for (Field campo : Payment.class.getDeclaredFields()) {
            if (campo.getName().equals(propriedade)) {
                Column c = campo.getAnnotation(Column.class);
                return c != null && !c.name().isEmpty() ? c.name() : snake(campo.getName());
            }
        }
        return null;
    }

    private static String snake(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    





























































































}
