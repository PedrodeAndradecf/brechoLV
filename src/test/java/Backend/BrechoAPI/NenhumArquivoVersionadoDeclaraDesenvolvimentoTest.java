package Backend.BrechoAPI;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;





























































































class NenhumArquivoVersionadoDeclaraDesenvolvimentoTest {

    






















































































    
    static final Path DOCKERFILE = Path.of("Dockerfile");
    static final Path STACK = Path.of("stack.yml");

    





    static final Pattern DECLARACAO_DE_AMBIENTE =
            Pattern.compile("brecho[._-]ambiente", Pattern.CASE_INSENSITIVE);

    
    static final Pattern ENDPOINT_DO_R2 =
            Pattern.compile("r2[._-]endpoint", Pattern.CASE_INSENSITIVE);

    







    static final Set<String> CONFIDENCIAIS = Set.of(
            "DB_PASSWORD", "JWT_SECRET", "R2_ACCESS_KEY_ID", "R2_SECRET_ACCESS_KEY",
            "POSTGRES_PASSWORD");
    static final Pattern NOME_CONFIDENCIAL =
            Pattern.compile("PASSWORD|SECRET|TOKEN", Pattern.CASE_INSENSITIVE);

    




    static final Pattern SO_NOME =
            Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*(:?\\?[^}]*)?}");

    

    @Test
    void dockerfile_gemeo_achaExpose8080EOEntrypointQueInvocaJavaComJavaOpts() throws IOException {
        List<String> instrucoes = instrucoesDoDockerfile(DOCKERFILE);
        assertGemeoDoDockerfile(instrucoes);
    }

    @Test
    void dockerfile_nenhumaInstrucaoDeclaraDesenvolvimento() throws IOException {
        List<String> instrucoes = instrucoesDoDockerfile(DOCKERFILE);
        assertGemeoDoDockerfile(instrucoes);

        assertThat(instrucoes)
                .as("instruções do %s que declaram brecho.ambiente/BRECHO_AMBIENTE (ADR 0017: "
                        + "produção é a ausência de declaração; 0021 §8.3)", DOCKERFILE.toAbsolutePath())
                .filteredOn(i -> DECLARACAO_DE_AMBIENTE.matcher(i).find())
                .isEmpty();
    }

    static void assertGemeoDoDockerfile(List<String> instrucoes) {
        assertThat(instrucoes)
                .as("gêmeo 1 (0021 §8.3): o leitor acha a instrução EXPOSE 8080 em %s — "
                        + "sem ela não está lendo o Dockerfile certo, e o verde não vale",
                        DOCKERFILE.toAbsolutePath())
                .anySatisfy(i -> {
                    assertThat(palavraChave(i)).isEqualTo("EXPOSE");
                    assertThat(argumentos(i).split("\\s+")).anyMatch(p -> p.equals("8080") || p.equals("8080/tcp"));
                });
        assertThat(instrucoes)
                .as("gêmeo 2 (0021 §8.3): o leitor acha o ENTRYPOINT que invoca java com $JAVA_OPTS "
                        + "em %s — é a porta por onde -Dbrecho.ambiente passaria",
                        DOCKERFILE.toAbsolutePath())
                .anySatisfy(i -> {
                    assertThat(palavraChave(i)).isEqualTo("ENTRYPOINT");
                    assertThat(argumentos(i)).containsPattern("\\bjava\\b")
                            .containsPattern("\\$\\{?JAVA_OPTS\\b");
                });
    }

    







    static List<String> instrucoesDoDockerfile(Path arquivo) throws IOException {
        assertThat(arquivo).as("o Dockerfile da raiz (0021 §8.3)").isRegularFile();
        List<String> instrucoes = new ArrayList<>();
        StringBuilder corrente = null;
        for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
            String semEspaco = linha.strip();
            if (semEspaco.startsWith("#")) {
                continue;
            }
            if (corrente == null && semEspaco.isEmpty()) {
                continue;
            }
            boolean continua = semEspaco.endsWith("\\");
            String pedaco = continua ? semEspaco.substring(0, semEspaco.length() - 1) : semEspaco;
            if (corrente == null) {
                corrente = new StringBuilder(pedaco);
            } else {
                corrente.append(' ').append(pedaco);
            }
            if (!continua) {
                instrucoes.add(corrente.toString().strip());
                corrente = null;
            }
        }
        if (corrente != null) {
            instrucoes.add(corrente.toString().strip());
        }
        return instrucoes;
    }

    private static String palavraChave(String instrucao) {
        return instrucao.split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
    }

    private static String argumentos(String instrucao) {
        String[] partes = instrucao.split("\\s+", 2);
        return partes.length > 1 ? partes[1] : "";
    }

    

    @Test
    void stack_gemeo_achaJwtSecretEDbHost() throws IOException {
        Map<String, Map<String, String>> ambientes = ambientesPorServico(carregarStack(STACK));
        assertGemeoDoStack(ambientes);
    }

    @Test
    void stack_naoDeclaraDesenvolvimento_naoPublicaPorta_semR2Endpoint_soNomeNosConfidenciais()
            throws IOException {
        Map<String, Object> stack = carregarStack(STACK);
        Map<String, Map<String, String>> ambientes = ambientesPorServico(stack);
        assertGemeoDoStack(ambientes);

        List<String> folhas = new ArrayList<>();
        folhas(stack, "", folhas);

        assertThat(folhas)
                .as("chaves/valores de %s que declaram brecho.ambiente/BRECHO_AMBIENTE "
                        + "(0021 §3; ADR 0017)", STACK.toAbsolutePath())
                .filteredOn(f -> DECLARACAO_DE_AMBIENTE.matcher(f).find())
                .isEmpty();

        assertThat(folhas)
                .as("chaves/valores de %s que declaram o endpoint do R2 (0021 D2: nunca em produção)",
                        STACK.toAbsolutePath())
                .filteredOn(f -> ENDPOINT_DO_R2.matcher(f).find())
                .isEmpty();

        Map<String, Object> publicadas = new LinkedHashMap<>();
        servicos(stack).forEach((nome, def) -> {
            if (def instanceof Map<?, ?> m && m.containsKey("ports")) {
                publicadas.put(nome, m.get("ports"));
            }
        });
        assertThat(publicadas)
                .as("serviços de %s com 'ports:' — nenhuma porta publicada no host, nem da "
                        + "aplicação nem do Postgres (spec F4-D6, S53; 0021 D2, §8.1-6)",
                        STACK.toAbsolutePath())
                .isEmpty();

        List<String> confidenciaisComValor = new ArrayList<>();
        ambientes.forEach((servico, env) -> env.forEach((chave, valor) -> {
            boolean confidencial = CONFIDENCIAIS.contains(chave) || NOME_CONFIDENCIAL.matcher(chave).find();
            if (confidencial && (valor == null || !SO_NOME.matcher(valor.strip()).matches())) {
                
                
                confidenciaisComValor.add(servico + "." + chave
                        + (valor == null ? " (sem valor)" : " (valor não impresso)"));
            }
        }));
        assertThat(confidenciaisComValor)
                .as("variáveis confidenciais de %s que não são só ${NOME} (0021 §3: o valor mora "
                        + "no env do stack, no Portainer; um literal ou um ${NOME:-padrão} é valor "
                        + "versionado)", STACK.toAbsolutePath())
                .isEmpty();
    }

    static void assertGemeoDoStack(Map<String, Map<String, String>> ambientes) {
        Map<String, String> todas = new LinkedHashMap<>();
        ambientes.values().forEach(todas::putAll);
        
        assertThat(todas.keySet())
                .as("gêmeo (0021 §3, §8.2): o leitor acha JWT_SECRET e DB_HOST no environment "
                        + "dos serviços de %s — sem eles não está lendo o stack certo",
                        STACK.toAbsolutePath())
                .contains("JWT_SECRET", "DB_HOST");
        
        
        
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> carregarStack(Path arquivo) throws IOException {
        assertThat(arquivo).as("o stack.yml da raiz (0021 §8.2)").isRegularFile();
        Object doc = new Yaml(new SafeConstructor(new LoaderOptions()))
                .load(Files.readString(arquivo, StandardCharsets.UTF_8));
        assertThat(doc).as("%s é um mapa YAML", arquivo).isInstanceOf(Map.class);
        return (Map<String, Object>) doc;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> servicos(Map<String, Object> stack) {
        Object s = stack.get("services");
        assertThat(s).as("'services:' do stack").isInstanceOf(Map.class);
        Map<String, Object> r = new LinkedHashMap<>();
        ((Map<Object, Object>) s).forEach((k, v) -> r.put(String.valueOf(k), v));
        return r;
    }

    



    static Map<String, Map<String, String>> ambientesPorServico(Map<String, Object> stack) {
        Map<String, Map<String, String>> r = new LinkedHashMap<>();
        servicos(stack).forEach((nome, def) -> {
            Map<String, String> env = new LinkedHashMap<>();
            if (def instanceof Map<?, ?> m) {
                Object e = m.get("environment");
                if (e instanceof Map<?, ?> em) {
                    em.forEach((k, v) -> env.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
                } else if (e instanceof List<?> el) {
                    for (Object item : el) {
                        String s = String.valueOf(item);
                        int i = s.indexOf('=');
                        env.put(i < 0 ? s : s.substring(0, i), i < 0 ? null : s.substring(i + 1));
                    }
                }
            }
            r.put(nome, env);
        });
        return r;
    }

    
    private static void folhas(Object no, String caminho, List<String> saida) {
        if (no instanceof Map<?, ?> m) {
            m.forEach((k, v) -> {
                String c = caminho.isEmpty() ? String.valueOf(k) : caminho + "." + k;
                saida.add(c);
                folhas(v, c, saida);
            });
        } else if (no instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) {
                folhas(l.get(i), caminho + "[" + i + "]", saida);
            }
        } else if (no != null) {
            saida.add(caminho + "=" + no);
        }
    }
}
