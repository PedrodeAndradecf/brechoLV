package Backend.BrechoAPI;

import Backend.BrechoAPI.service.StorageService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.TypeExcludeFilter;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
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





































































































class GuardaDaUrlPublicaDoR2Test {

    


























































































































































































































































    private static final String CAMINHO_DA_CHAVE = "r2.public-url";

    
    private static final String URL_BOA = "https://fotos.brecho-exemplo.com.br/brecho-fotos";

    
    private static final String LITERAL_CRU = "${R2_PUBLIC_URL}";

    





    private static final String ENTREGA_ESCAPADA = "\\" + LITERAL_CRU;

    




    private static final String JWT_VALIDO = Base64.getEncoder().encodeToString(
            "QA-DV3b: segredo valido so de teste, 2026-10-12, com 256 bits+"
                    .getBytes(StandardCharsets.UTF_8));

    private static final PostgreSQLContainer<?> pg =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @BeforeAll
    static void preCondicoesESobeOBanco() {
        
        
        
        assertThat(System.getenv("R2_PUBLIC_URL"))
                .as("esta máquina não pode ter R2_PUBLIC_URL no ambiente — o caso vazio mede a AUSÊNCIA")
                .isNull();
        assertThat(System.getenv("JWT_SECRET"))
                .as("esta máquina não pode ter JWT_SECRET no ambiente — o segredo destes contextos é o do teste")
                .isNull();
        assertThat(System.getenv("BRECHO_AMBIENTE"))
                .as("esta máquina não pode ter BRECHO_AMBIENTE no ambiente — produção é a ausência (ADR 0017)")
                .isNull();
        assertThat(System.getProperty("brecho.ambiente"))
                .as("a JVM do teste não pode ter -Dbrecho.ambiente — produção é a ausência (ADR 0017)")
                .isNull();
        pg.start();
    }

    @AfterAll
    static void derrubaOBanco() {
        pg.stop();
    }

    

    








    @Test
    void casoBom_comUrlPublicaBoaSobeEOValorChegaAoCampo() {
        Map<String, Object> ambiente = producaoComBanco();
        ambiente.put("R2_PUBLIC_URL", URL_BOA);

        try (ConfigurableApplicationContext ctx = sobe(ambiente,
                "com R2_PUBLIC_URL válida a aplicação tem de subir — um guarda que recusa "
                        + "também o valor bom não é o guarda da DV3 (medição de 05/10, item 2 "
                        + "do log do ESTADO: o padrão JWT_SECRET à letra rejeitaria o bom)")) {
            assertThat(ctx.getBeanNamesForType(PostgreSQLContainer.class))
                    .as("o contexto deste teste não pode ter container de @ServiceConnection "
                            + "(TestcontainersConfiguration vazou pela varredura)")
                    .isEmpty();

            String urlMontada = ctx.getBean(StorageService.class).buildPublicUrl("alface.jpg");
            assertThat(urlMontada)
                    .as("o valor configurado tem de CHEGAR ao campo — subida sozinha seria "
                            + "satisfeita por uma entrega com erro de grafia na chave, verde pelo "
                            + "motivo errado (modo 4, SISTEMA.md §15). URL montada: %s", urlMontada)
                    .startsWith(URL_BOA);
        }
    }

    























    @Test
    void casoVazio_semR2PublicUrlNaoSobe() {
        
        
        
        StandardEnvironment sonda = new StandardEnvironment();
        sonda.getPropertySources().addFirst(new MapPropertySource("dv3b-premissa-vazio",
                Map.of(CAMINHO_DA_CHAVE, "${R2_PUBLIC_URL:}")));
        assertThat(sonda.getProperty(CAMINHO_DA_CHAVE))
                .as("premissa: a linha real de src/main (${R2_PUBLIC_URL:}) resolve para a string "
                        + "VAZIA com a variável ausente — é o default calado que este caso recusa; "
                        + "o application-default.properties de teste não existe no artefato empacotado")
                .isEqualTo("");

        Map<String, Object> ambiente = producaoComBanco();
        
        
        
        ambiente.put(CAMINHO_DA_CHAVE, "");

        Throwable recusa = recusaDeSubida(ambiente,
                "com r2.public-url VAZIO (o default da linha ${R2_PUBLIC_URL:}) a aplicação "
                        + "tem de recusar subir — o default silencioso é a string vazia "
                        + "(planejamento.md §10, entrada R2_PUBLIC_URL; régua do R2_ENDPOINT em "
                        + "SISTEMA.md §9: default de conveniência mascara a rota de produção)");

        assertRecusaNomeiaOCampo(recusa, "vazio");
    }

    






    @Test
    void casoLiteral_naoExpandidoNoCampoNaoSobe() {
        
        
        StandardEnvironment sonda = new StandardEnvironment();
        sonda.getPropertySources().addFirst(new MapPropertySource("dv3b-premissa",
                Map.of(CAMINHO_DA_CHAVE, ENTREGA_ESCAPADA)));
        String resolvido;
        try {
            resolvido = sonda.getProperty(CAMINHO_DA_CHAVE);
        } catch (RuntimeException naoEscapou) {
            throw new AssertionError("a premissa da entrega caiu: o resolvedor tinha de devolver "
                    + "o literal cru " + LITERAL_CRU + " a partir da forma escapada, e em vez "
                    + "disso lançou — se o escape do Spring mudou, remedir a ENTREGA do literal, "
                    + "não afrouxar o caso: " + naoEscapou, naoEscapou);
        }
        assertThat(resolvido)
                .as("premissa (medida 2026-10-12, spring-core 7.0.8): a forma escapada chega como "
                        + "o literal cru, sem resolver — é o que faz o literal ALCANÇAR o guarda")
                .isEqualTo(LITERAL_CRU);

        Map<String, Object> ambiente = producaoComBanco();
        ambiente.put(CAMINHO_DA_CHAVE, ENTREGA_ESCAPADA);

        Throwable recusa = recusaDeSubida(ambiente,
                "com o literal não expandido " + LITERAL_CRU + " no campo a aplicação tem de "
                        + "recusar subir — a medição de 05/10 (log do ESTADO, item 2) provou que "
                        + "hasText(\"${X}\") é true e o literal passa por guarda de vazio; o "
                        + "guarda da DV3-a tem de recusar TAMBÉM o literal");

        assertRecusaNomeiaOCampo(recusa, "o literal não expandido");
    }

    

    
    private static Map<String, Object> producaoComBanco() {
        Map<String, Object> ambiente = new HashMap<>();
        ambiente.put("DB_HOST", pg.getHost());
        ambiente.put("DB_PORT", String.valueOf(pg.getFirstMappedPort()));
        ambiente.put("DB_NAME", pg.getDatabaseName());
        ambiente.put("DB_USER", pg.getUsername());
        ambiente.put("DB_PASSWORD", pg.getPassword());
        ambiente.put("JWT_SECRET", JWT_VALIDO);
        ambiente.put("server.port", "0");
        ambiente.put("spring.main.banner-mode", "off");
        return ambiente;
    }

    private static ConfigurableApplicationContext subir(Map<String, Object> ambiente) {
        return new SpringApplicationBuilder(BrechoApiApplication.class)
                .web(WebApplicationType.SERVLET)
                .initializers(ctx -> {
                    
                    ctx.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("dv3b-ambiente-do-teste", ambiente));
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

    
    private static ConfigurableApplicationContext sobe(Map<String, Object> ambiente, String oQueIssoProvaria) {
        try {
            return subir(ambiente);
        } catch (RuntimeException | Error falha) {
            throw new AssertionError("a aplicação NÃO subiu — " + oQueIssoProvaria
                    + ". Cadeia da falha:\n" + textoDaCadeia(falha), falha);
        }
    }

    






    private static void assertRecusaNomeiaOCampo(Throwable recusa, String qualCaso) {
        String cadeia = textoDaCadeia(recusa);
        assertThat(cadeia)
                .as("a recusa (caso: %s) tem de nomear r2.public-url/R2_PUBLIC_URL — recusa que "
                        + "não nomeia o campo não orienta quem opera, e \"não sobe\" por outra "
                        + "causa seria verde pelo motivo errado. Cadeia real:%n%s", qualCaso, cadeia)
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
