package Backend.BrechoAPI;

import Backend.BrechoAPI.service.StorageService;
import Backend.BrechoAPI.service.impl.R2StorageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;























































































































class HostPrivadoDoR2RecusadoTest {

    






































    
    private static final String CONTA = "f00dfacef00dfacef00dfacef00dface";

    
    private static final String BUCKET = "bucket-inventado";

    private static final String S3_COM_CAMINHO =
            "https://" + CONTA + ".r2.cloudflarestorage.com/" + BUCKET;
    private static final String S3_SEM_CAMINHO =
            "https://" + CONTA + ".r2.cloudflarestorage.com";

    
    private static final String PUB_R2_DEV = "https://pub-0123456789abcdef0123456789abcdef.r2.dev";

    private static final String DOMINIO_PROPRIO = "https://fotos.brecho-exemplo.com.br";

    private static final String JWT_VALIDO = Base64.getEncoder().encodeToString(
            "QA-F4-D5: segredo valido so de teste, 2026-10-23, com 256 bits+"
                    .getBytes(StandardCharsets.UTF_8));

    private static final PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @BeforeAll
    static void preCondicoesESobeOBanco() {
        assertThat(System.getenv("R2_PUBLIC_URL"))
                .as("esta máquina não pode ter R2_PUBLIC_URL no ambiente — o valor é o do teste")
                .isNull();
        assertThat(System.getenv("JWT_SECRET"))
                .as("esta máquina não pode ter JWT_SECRET no ambiente")
                .isNull();
        assertThat(System.getenv("BRECHO_AMBIENTE"))
                .as("esta máquina não pode ter BRECHO_AMBIENTE — produção é a ausência (ADR 0017)")
                .isNull();
        assertThat(System.getProperty("brecho.ambiente"))
                .as("a JVM do teste não pode ter -Dbrecho.ambiente — produção é a ausência")
                .isNull();
        pg.start();
    }

    @AfterAll
    static void derrubaOBanco() {
        pg.stop();
    }

    

    @Test
    void contexto_hostS3PrivadoComCaminho_naoSobe() {
        Throwable recusa = recusaDeSubida(comR2PublicUrl(S3_COM_CAMINHO),
                "com R2_PUBLIC_URL=" + S3_COM_CAMINHO + " (o endpoint S3 privado, que dá 400 sem "
                        + "assinatura — o achado de 16/10 §2) a aplicação tem de recusar subir (P9 (ii))");
        assertCadeiaNomeiaAPropriedade(recusa, S3_COM_CAMINHO);
    }

    @Test
    void contexto_hostS3PrivadoSemCaminho_naoSobe() {
        Throwable recusa = recusaDeSubida(comR2PublicUrl(S3_SEM_CAMINHO),
                "com R2_PUBLIC_URL=" + S3_SEM_CAMINHO + " (host S3 privado sem caminho) a aplicação "
                        + "tem de recusar subir (P9 (ii); a spec nomeia \"com e sem caminho\")");
        assertCadeiaNomeiaAPropriedade(recusa, S3_SEM_CAMINHO);
    }

    @Test
    void contexto_gemeo_pubR2Dev_sobeEOValorChega() {
        sobeEOValorChega(PUB_R2_DEV);
    }

    @Test
    void contexto_gemeo_dominioProprio_sobeEOValorChega() {
        sobeEOValorChega(DOMINIO_PROPRIO);
    }

    

    @ParameterizedTest(name = "recusa: {0}")
    @ValueSource(strings = {
            "https://" + CONTA + ".r2.cloudflarestorage.com/" + BUCKET,
            "https://" + CONTA + ".r2.cloudflarestorage.com",
            "https://" + CONTA + ".r2.cloudflarestorage.com/" + BUCKET + "/",
            "https://F00DFACEF00DFACEF00DFACEF00DFACE.R2.CloudflareStorage.COM/" + BUCKET,
            "https://" + CONTA + ".r2.cloudflarestorage.com:443/" + BUCKET,
            "https://" + CONTA + ".eu.r2.cloudflarestorage.com/" + BUCKET,
            "http://" + CONTA + ".r2.cloudflarestorage.com/" + BUCKET,
    })
    void construtor_hostS3PrivadoRecusado(String url) {
        String mensagem = mensagemDaRecusa(url,
                "o host de " + url + " está sob r2.cloudflarestorage.com (o endpoint S3 privado) "
                        + "e o construtor o aceitou — P9 (ii) manda recusar");
        assertMensagemNomeiaESemProcesso(mensagem);
    }

    @ParameterizedTest(name = "aceita: {0}")
    @ValueSource(strings = {
            "https://pub-0123456789abcdef0123456789abcdef.r2.dev",
            "https://fotos.brecho-exemplo.com.br",
            "https://r2.cloudflarestorage.com.brecho-exemplo.com.br",
            "https://fotos.brecho-exemplo.com.br/r2.cloudflarestorage.com",
    })
    void construtor_gemeo_urlPublicaAceita(String url) {
        R2StorageService servico;
        try {
            servico = new R2StorageService(null, BUCKET, url);
        } catch (RuntimeException recusa) {
            throw new AssertionError("o construtor recusou " + url + " — o host dela NÃO está sob "
                    + "r2.cloudflarestorage.com; um guarda que procura o sufixo na URL inteira ou "
                    + "por contains no host recusa a URL pública legítima. Recusa: " + recusa, recusa);
        }
        assertThat(servico.buildPublicUrl("alface.jpg"))
                .as("o valor aceito tem de chegar ao campo")
                .startsWith(url);
    }

    @ParameterizedTest(name = "DV3-a continua recusando: \"{0}\"")
    @ValueSource(strings = {"", "${R2_PUBLIC_URL}"})
    void construtor_gemeo_casosDaDV3aContinuamRecusando(String valor) {
        String mensagem = mensagemDaRecusa(valor,
                "a guarda da DV3-a recusava \"" + valor + "\" e deixou de recusar — a extensão "
                        + "não pode afrouxar o que já existia");
        assertMensagemNomeiaESemProcesso(mensagem);
    }

    

    private static String mensagemDaRecusa(String url, String seAceitou) {
        try {
            new R2StorageService(null, BUCKET, url);
        } catch (RuntimeException recusa) {
            assertThat(recusa.getMessage()).as("a recusa tem mensagem").isNotBlank();
            return recusa.getMessage();
        }
        throw new AssertionError(seAceitou);
    }

    private static void assertMensagemNomeiaESemProcesso(String mensagem) {
        assertThat(mensagem)
                .as("a mensagem nomeia a variável ou a propriedade — quem lê o log no Portainer "
                        + "precisa saber o que corrigir. Mensagem:%n%s", mensagem)
                .containsAnyOf("R2_PUBLIC_URL", "r2.public-url");
        assertThat(MensagensDeRecusaSemProcessoTest.termosDeProcessoEm(mensagem))
                .as("a mensagem de recusa cita processo (P11 (b), o par da F4-D4). Mensagem:%n%s", mensagem)
                .isEmpty();
    }

    

    private static Map<String, Object> comR2PublicUrl(String url) {
        Map<String, Object> ambiente = new HashMap<>();
        ambiente.put("DB_HOST", pg.getHost());
        ambiente.put("DB_PORT", String.valueOf(pg.getFirstMappedPort()));
        ambiente.put("DB_NAME", pg.getDatabaseName());
        ambiente.put("DB_USER", pg.getUsername());
        ambiente.put("DB_PASSWORD", pg.getPassword());
        ambiente.put("JWT_SECRET", JWT_VALIDO);
        ambiente.put("R2_PUBLIC_URL", url);
        ambiente.put("server.port", "0");
        ambiente.put("spring.main.banner-mode", "off");
        return ambiente;
    }

    private static void sobeEOValorChega(String url) {
        ConfigurableApplicationContext ctx;
        try {
            ctx = subir(comR2PublicUrl(url));
        } catch (RuntimeException | Error falha) {
            throw new AssertionError("a aplicação NÃO subiu com R2_PUBLIC_URL=" + url + " — é uma "
                    + "URL pública legítima, e a extensão da guarda não pode recusá-la. Cadeia:\n"
                    + textoDaCadeia(falha), falha);
        }
        try (ctx) {
            assertThat(ctx.getBeanNamesForType(PostgreSQLContainer.class))
                    .as("TestcontainersConfiguration vazou pela varredura")
                    .isEmpty();
            assertThat(ctx.getBean(StorageService.class).buildPublicUrl("alface.jpg"))
                    .as("o valor configurado tem de CHEGAR ao campo — subir com outro valor "
                            + "(erro de grafia na chave) seria verde pelo motivo errado")
                    .startsWith(url);
        }
    }

    private static ConfigurableApplicationContext subir(Map<String, Object> ambiente) {
        return new SpringApplicationBuilder(BrechoApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(ctx -> {
                    ctx.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("f4d5-ambiente-do-teste", ambiente));
                    ctx.getBeanFactory().registerSingleton(SoSrcMain.class.getName(), new SoSrcMain());
                })
                .run();
    }

    private static Throwable recusaDeSubida(Map<String, Object> ambiente, String oQueIssoProvaria) {
        ConfigurableApplicationContext ctx = null;
        try {
            ctx = subir(ambiente);
        } catch (RuntimeException | Error falha) {
            return falha;
        } finally {
            if (ctx != null) {
                ctx.close();
            }
        }
        throw new AssertionError("a aplicação SUBIU — " + oQueIssoProvaria);
    }

    




    private static void assertCadeiaNomeiaAPropriedade(Throwable recusa, String url) {
        String cadeia = textoDaCadeia(recusa);
        assertThat(cadeia)
                .as("a aplicação não subiu com R2_PUBLIC_URL=%s, mas a cadeia não nomeia "
                        + "r2.public-url/R2_PUBLIC_URL — é queda por outro motivo (erro de contexto), "
                        + "não a recusa pedida. Cadeia real:%n%s", url, cadeia)
                .containsAnyOf("r2.public-url", "R2_PUBLIC_URL");
    }

    private static String textoDaCadeia(Throwable t) {
        StringBuilder texto = new StringBuilder();
        for (Throwable atual = t; atual != null;
                atual = atual.getCause() == atual ? null : atual.getCause()) {
            texto.append(atual.getClass().getName()).append(": ")
                    .append(atual.getMessage()).append('\n');
        }
        return texto.toString();
    }

    
    static final class SoSrcMain extends TypeExcludeFilter {
        @Override
        public boolean match(MetadataReader leitor, MetadataReaderFactory fabrica) throws IOException {
            return leitor.getResource().getURL().toString().contains("/test-classes/");
        }

        @Override
        public boolean equals(Object o) {
            return o != null && o.getClass() == getClass();
        }

        @Override
        public int hashCode() {
            return SoSrcMain.class.hashCode();
        }
    }
}
