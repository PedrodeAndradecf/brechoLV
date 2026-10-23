package Backend.BrechoAPI;

import Backend.BrechoAPI.config.AmbienteDeExecucao;
import Backend.BrechoAPI.config.PaymentReviewProperties;
import Backend.BrechoAPI.service.impl.R2StorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;























































class MensagensDeRecusaSemProcessoTest {

    
    private static final List<Pattern> TERMOS_DE_PROCESSO = List.of(
            Pattern.compile("planning/"),
            Pattern.compile("SISTEMA\\.md"),
            Pattern.compile("COLHEITA"),
            Pattern.compile("docs/adr"),
            Pattern.compile("\\bADR\\s*\\d"),
            Pattern.compile("reviews/"),
            Pattern.compile("planejamento\\.md"),
            Pattern.compile("\\bPedro\\b"),
            Pattern.compile("\\bESTADO"));

    

    @Test
    void m1_ambienteDesconhecido() {
        String msg = mensagemDe(() -> new AmbienteDeExecucao("producao"));
        assertSemProcessoENomeia(msg, "brecho.ambiente", "BRECHO_AMBIENTE");
    }

    @Test
    void m2_pendingAfterAusente() {
        String msg = mensagemDe(() -> new PaymentReviewProperties(null));
        assertSemProcessoENomeia(msg, "payment.review.pending-after", "PAYMENT_REVIEW_PENDING_AFTER");
    }

    
    

    @Test
    void m4_urlPublicaDoR2Vazia() {
        String msg = mensagemDe(() -> new R2StorageService(null, "bucket", ""));
        assertSemProcessoENomeia(msg, "R2_PUBLIC_URL", "r2.public-url");
    }

    @Test
    void m5_urlPublicaDoR2PlaceholderNaoExpandido() {
        String msg = mensagemDe(() -> new R2StorageService(null, "bucket", "${R2_PUBLIC_URL}"));
        assertSemProcessoENomeia(msg, "R2_PUBLIC_URL", "r2.public-url");
    }

    

    
    @Test
    void gemeo_termoPlantadoEAcusado() {
        List<String> plantadas = List.of(
                "defina X (planning/fila.md)",
                "defina X (SISTEMA.md §9)",
                "defina X (COLHEITA 55)",
                "defina X (docs/adr/0017)",
                "defina X (ADR 0017 §2.1)",
                "defina X (reviews/sprint1.md)",
                "defina X (planejamento.md §10)",
                "defina X (decisão do Pedro 07/10)",
                "defina X (item 2 do log do ESTADO)");
        for (String plantada : plantadas) {
            assertThat(termosDeProcessoEm(plantada))
                    .as("o detector tem de acusar o termo plantado em: %s", plantada)
                    .isNotEmpty();
        }
        assertThat(plantadas).hasSameSizeAs(TERMOS_DE_PROCESSO);
    }

    
    @Test
    void gemeo_fraseLimpaNaoEAcusada() {
        assertThat(termosDeProcessoEm(
                "r2.public-url está vazio — defina R2_PUBLIC_URL com a URL pública do bucket;"
                        + " a aplicação recusa subir em vez de devolver URL sem host"))
                .isEmpty();
    }

    

    
    static String mensagemDe(Executable recusa) {
        RuntimeException e = assertThrows(RuntimeException.class, recusa,
                "a recusa não aconteceu — o teste não leu mensagem nenhuma");
        assertThat(e.getMessage()).as("a recusa tem mensagem").isNotBlank();
        return e.getMessage();
    }

    
    static void assertSemProcessoENomeia(String msg, String... nomesAceitos) {
        assertThat(termosDeProcessoEm(msg))
                .as("a mensagem de recusa cita processo (P11 (b), S49). Mensagem:%n%s", msg)
                .isEmpty();
        assertThat(msg)
                .as("gêmeo: a mensagem nomeia a variável ou propriedade que causou a recusa")
                .containsAnyOf(nomesAceitos);
    }

    
    static List<String> termosDeProcessoEm(String texto) {
        List<String> achados = new ArrayList<>();
        for (Pattern termo : TERMOS_DE_PROCESSO) {
            Matcher m = termo.matcher(texto);
            while (m.find()) {
                achados.add(m.group());
            }
        }
        return achados;
    }
}
