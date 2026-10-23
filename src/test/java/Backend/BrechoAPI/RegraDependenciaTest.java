package Backend.BrechoAPI;

import Backend.BrechoAPI.domain.Order;
import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.domain.enums.PaymentStatus;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.repository.PaymentRepository;
import Backend.BrechoAPI.service.CartService;
import Backend.BrechoAPI.service.CheckoutTransactionService;
import Backend.BrechoAPI.service.OrderExpiryService;
import Backend.BrechoAPI.service.OrderService;
import Backend.BrechoAPI.service.PaymentReviewService;
import Backend.BrechoAPI.service.ProductService;
import Backend.BrechoAPI.service.ShippingService;
import Backend.BrechoAPI.service.StorageService;
import Backend.BrechoAPI.service.impl.R2StorageService;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClient;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;












@AnalyzeClasses(packages = "Backend.BrechoAPI")
class RegraDependenciaTest {

    @ArchTest
    static final ArchRule controllerNaoAcessaRepository = noClasses()
            .that().resideInAPackage("..controller..")
            .should().accessClassesThat().resideInAPackage("..repository..")
            .because("controller fala com service; pular direto pro repository reabre a camada que a Fase 1 fechou");

    @ArchTest
    static final ArchRule domainNaoConheceCamadasDeFora = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..service..", "..dto..")
            .because("domínio não pode depender de quem depende dele — senão a regra de negócio vaza pra fora");

    @ArchTest
    static final ArchRule controllerNaoExpoeEntidadeJpaNaAssinatura = noMethods()
            .that().areDeclaredInClassesThat().resideInAPackage("..controller..")
            .and().arePublic()
            .should().haveRawReturnType(annotatedWith(Entity.class))
            .orShould().haveRawParameterTypes(describe(
                    "conter uma entidade JPA (@Entity)",
                    params -> params.stream().anyMatch(p -> p.isAnnotatedWith(Entity.class))))
            .because("controller devolve e recebe DTO — entidade JPA na assinatura vaza coluna do banco pro contrato HTTP");

    











    @ArchTest
    static final ArchRule semChamadaDiretaANow = classes()
            .should(new ArchCondition<JavaClass>(
                    "chamar .now() de java.time diretamente — hora vem do Clock injetado") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getMethodCallsFromSelf().stream()
                            .filter(call -> call.getTarget().getName().equals("now"))
                            .filter(call -> call.getTargetOwner().getPackageName().startsWith("java.time"))
                            .forEach(call -> events.add(SimpleConditionEvent.violated(call,
                                    call.getOrigin().getFullName() + " chama "
                                            + call.getTargetOwner().getSimpleName() + ".now() direto"
                                            + " — deveria receber o instante de um Clock injetado")));
                }
            });

    @ArchTest
    static final ArchRule semAutoInvocacaoDeTransactional = classes()
            .should(new ArchCondition<JavaClass>(
                    "chamar método @Transactional da própria classe — self-invocation não passa pelo proxy do Spring") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getMethodCallsFromSelf().stream()
                            .filter(call -> call.getTargetOwner().equals(javaClass))
                            .filter(call -> call.getTarget().resolveMember()
                                    .map(m -> m.isAnnotatedWith(Transactional.class))
                                    .orElse(false))
                            .forEach(call -> events.add(SimpleConditionEvent.violated(call,
                                    call.getOrigin().getFullName() + " chama " + call.getTarget().getName()
                                            + "() (@Transactional) na própria classe — o proxy do Spring não intercepta")));
                }
            });

    













    @ArchTest
    static final ArchRule rotaPaginadaNaoColideComParametroDePaginacao = classes()
            .should(new ArchCondition<JavaClass>(
                    "declarar @RequestParam chamado page/size/sort em método que também recebe Pageable"
                            + " — os dois leem a mesma chave da query string") {

                private static final java.util.Set<String> RESERVADOS =
                        java.util.Set.of("page", "size", "sort");

                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getMethods().stream()
                            .filter(m -> m.getRawParameterTypes().stream()
                                    .anyMatch(t -> t.getName().equals("org.springframework.data.domain.Pageable")))
                            .forEach(m -> {
                                for (Parameter param : m.reflect().getParameters()) {
                                    RequestParam anotacao = param.getAnnotation(RequestParam.class);
                                    if (anotacao == null) {
                                        continue;
                                    }
                                    String explicito = !anotacao.name().isEmpty()
                                            ? anotacao.name()
                                            : anotacao.value();

                                    if (explicito.isEmpty() && !param.isNamePresent()) {
                                        
                                        
                                        
                                        
                                        events.add(SimpleConditionEvent.violated(m, m.getFullName()
                                                + " tem @RequestParam sem name= e o bytecode não guarda o nome"
                                                + " do parâmetro (compile com -parameters) — impossível"
                                                + " verificar colisão com page/size/sort"));
                                        continue;
                                    }

                                    String nomeHttp = explicito.isEmpty() ? param.getName() : explicito;
                                    if (RESERVADOS.contains(nomeHttp)) {
                                        events.add(SimpleConditionEvent.violated(m, m.getFullName()
                                                + " declara @RequestParam \"" + nomeHttp + "\" e também recebe"
                                                + " Pageable — o Spring Data lê page/size/sort da mesma query"
                                                + " string, então o valor vira filtro E paginação ao mesmo"
                                                + " tempo (ver SISTEMA.md §14)"));
                                    }
                                }
                            });
                }
            })
            .because("um filtro com nome reservado da paginação vira dois parâmetros silenciosamente");

    





    @ArchTest
    static final ArchRule semCiclosEntrePacotes = slices()
            .matching("Backend.BrechoAPI.(*)..")
            .should().beFreeOfCycles();

    

















































    








    private static final Set<String> PACOTES_DE_FORNECEDOR = Set.of(
            "org.springframework.web.client",
            "org.springframework.web.reactive.function.client",
            "org.springframework.http.client",
            "java.net.http",
            "okhttp3",
            "retrofit2",
            "org.apache.hc",
            "org.apache.http",
            "feign",
            "com.abacatepay",
            "software.amazon.awssdk");

    
    private static final Set<String> CLASSES_DE_FORNECEDOR = Set.of(
            "java.net.URLConnection",
            "java.net.HttpURLConnection",
            "java.net.Socket");

    
    private static final Set<String> ZONA_DE_INTEGRACAO = Set.of(
            "Backend.BrechoAPI.service.impl",
            "Backend.BrechoAPI.config");

    private static final DescribedPredicate<JavaClass> FORA_DA_ZONA_DE_INTEGRACAO = describe(
            "fora de Backend.BrechoAPI.service.impl (adaptador) e Backend.BrechoAPI.config (montagem)",
            javaClass -> ZONA_DE_INTEGRACAO.stream()
                    .noneMatch(pacote -> dentroDe(javaClass.getPackageName(), pacote)));

    







    private static final DescribedPredicate<JavaClass> DECLARACAO_DE_ARQUITETURA = describe(
            "o próprio RegraDependenciaTest ou uma fixture aninhada nele",
            javaClass -> javaClass.getName().equals(RegraDependenciaTest.class.getName())
                    || javaClass.getName().startsWith(RegraDependenciaTest.class.getName() + "$"));

    

















    private static final String PONTE_DO_BANCO_POR_VARIAVEL = BancoPorVariavelTest.Ponte.class.getName();

    private static final DescribedPredicate<JavaClass> PONTE_TCP_PARA_O_CONTAINER_DE_TESTE = describe(
            "a Ponte TCP do BancoPorVariavelTest (repasse até o Postgres do Testcontainers)",
            javaClass -> javaClass.getName().equals(PONTE_DO_BANCO_POR_VARIAVEL));

    




    private static final DescribedPredicate<JavaClass> CLASSE_DE_TESTE = describe(
            "compilada em target/test-classes",
            javaClass -> javaClass.getSource()
                    .map(fonte -> fonte.getUri().toString().contains("/test-classes/"))
                    .orElse(false));

    




    private static final DescribedPredicate<JavaClass> ESCOPO_DA_REGRA_DO_FORNECEDOR =
            FORA_DA_ZONA_DE_INTEGRACAO
                    .and(DescribedPredicate.not(DECLARACAO_DE_ARQUITETURA))
                    .and(DescribedPredicate.not(PONTE_TCP_PARA_O_CONTAINER_DE_TESTE));

    









    private static final DescribedPredicate<JavaClass> ESCOPO_DA_REGRA_DA_PORTA =
            FORA_DA_ZONA_DE_INTEGRACAO.and(DescribedPredicate.not(CLASSE_DE_TESTE));

    




    private static final ArchCondition<JavaClass> DEPENDER_DE_FORNECEDOR =
            new ArchCondition<JavaClass>(
                    "depender de cliente HTTP ou SDK de fornecedor fora do adaptador") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getDirectDependenciesFromSelf().stream()
                            .filter(dep -> ehFornecedor(dep.getTargetClass().getName()))
                            .forEach(dep -> events.add(SimpleConditionEvent.violated(dep,
                                    javaClass.getName() + " depende de "
                                            + dep.getTargetClass().getName()
                                            + " — cliente HTTP/SDK de fornecedor só pode aparecer no"
                                            + " adaptador (Backend.BrechoAPI.service.impl) ou na montagem"
                                            + " do bean (Backend.BrechoAPI.config); o resto do sistema fala"
                                            + " com a porta (ADR 0002). Onde: " + dep.getDescription())));
                }
            };

    private static final ArchCondition<JavaClass> DEPENDER_DE_ADAPTADOR_CONCRETO =
            new ArchCondition<JavaClass>(
                    "depender de uma implementação de Backend.BrechoAPI.service.impl em vez da interface") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getDirectDependenciesFromSelf().stream()
                            .filter(dep -> dentroDe(dep.getTargetClass().getPackageName(),
                                    "Backend.BrechoAPI.service.impl"))
                            .forEach(dep -> events.add(SimpleConditionEvent.violated(dep,
                                    javaClass.getName() + " depende de "
                                            + dep.getTargetClass().getName()
                                            + " — adaptador concreto. Quem não é o próprio adaptador nem a"
                                            + " montagem do bean depende da interface em"
                                            + " Backend.BrechoAPI.service (ADR 0002). Onde: "
                                            + dep.getDescription())));
                }
            };

    @ArchTest
    static final ArchRule soOAdaptadorTocaOFornecedor = classes()
            .that(ESCOPO_DA_REGRA_DO_FORNECEDOR)
            .should(DEPENDER_DE_FORNECEDOR)
            .because("tipo de gateway dentro do domínio ou do serviço é a coisa mais fácil de vazar"
                    + " e a mais cara de desvazar — e teste que precisa de rede não é teste (ADR 0002)");

    @ArchTest
    static final ArchRule dependenciaEhPelaPortaNaoPeloAdaptador = classes()
            .that(ESCOPO_DA_REGRA_DA_PORTA)
            .should(DEPENDER_DE_ADAPTADOR_CONCRETO)
            .because("a sprint tem dois adaptadores para a mesma porta (falso e AbacatePay); quem"
                    + " injeta o concreto escolhe um dos dois no lugar errado (ADR 0002)");

    






























    













    private static final Set<String> QUEM_PODE_CALCULAR_FRETE = Set.of(
            "Backend.BrechoAPI.service.CheckoutTransactionService",
            "Backend.BrechoAPI.service.ShippingService");

    





    private static final DescribedPredicate<JavaClass> ESCOPO_DA_REGRA_DO_FRETE = describe(
            "classe de produção que não é o checkout nem o próprio ShippingService",
            javaClass -> !CLASSE_DE_TESTE.test(javaClass)
                    && !QUEM_PODE_CALCULAR_FRETE.contains(javaClass.getName()));

    



    private static final ArchCondition<JavaClass> CALCULAR_FRETE =
            new ArchCondition<JavaClass>(
                    "chamar ShippingService.costFor fora do checkout — frete é valor congelado") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getMethodCallsFromSelf().stream()
                            .filter(call -> call.getTarget().getName().equals("costFor"))
                            .filter(call -> call.getTargetOwner().getName()
                                    .equals(ShippingService.class.getName()))
                            .forEach(call -> events.add(SimpleConditionEvent.violated(call,
                                    call.getOrigin().getFullName() + " chama"
                                            + " ShippingService.costFor() — o frete é CONGELADO em"
                                            + " orders.shipping_cost no checkout, e só o checkout o"
                                            + " calcula. Recalcular na retomada ou na leitura faz o"
                                            + " pedido de janeiro mostrar a tabela de março, sem"
                                            + " nada avisando entre os dois dias (ADR 0003). Onde: "
                                            + call.getDescription())));
                }
            };

    






































    @ArchTest
    static final ArchRule soOCheckoutCalculaFrete = classes()
            .that(ESCOPO_DA_REGRA_DO_FRETE)
            .should(CALCULAR_FRETE)
            .because("o pedido congela VALORES: shipping_cost tem um produtor só, e o dano de um"
                    + " segundo é silencioso — nenhuma asserção de valor o pega (ADR 0003)");

    






































































































































































    






    



    private static final String NOME_DA_COBRANCA =
            "(?:[\"`]?[\\w$]+[\"`]?\\.)*[\"`]?payments?[\"`]?(?![\\w$])";

    


    private static final Pattern ESCRITA_NA_COBRANCA = Pattern.compile(
            "(?is)\\b(?:update|merge\\s+into|insert\\s+into)\\s+(?:only\\s+|versioned\\s+)?" + NOME_DA_COBRANCA);

    


    private static final Pattern CLAUSULA_SET =
            Pattern.compile("(?is)\\bset\\b(.*?)(?:\\bwhere\\b|$)");

    


    private static final Pattern MENCAO_A_STATUS =
            Pattern.compile("(?is)(?<![\\w$])[\"`]?status[\"`]?(?![\\w$])");

    

    private static final Pattern ATRIBUICAO_DE_STATUS = Pattern.compile(
            "(?is)(?<![\\w$])[\"`]?status[\"`]?\\s*=\\s*('[^']*'|[^\\s,)]+)");

    


    private static final Pattern LITERAL_OPEN =
            Pattern.compile("^(?:'OPEN'|(?:[\\w$]+\\.)*OPEN)$");

    










    static boolean soltaCobrancaPendente(JavaMethod metodo) {
        return metodo.getAnnotations().stream()
                .filter(RegraDependenciaTest::declaraConsulta)
                .map(anotacao -> anotacao.get("value").map(String::valueOf).orElse(""))
                .anyMatch(RegraDependenciaTest::ehSoltador);
    }

    private static boolean declaraConsulta(JavaAnnotation<?> anotacao) {
        JavaClass tipo = anotacao.getRawType();
        return tipo.isEquivalentTo(Query.class)
                || tipo.isAnnotatedWith(Query.class)
                || tipo.isMetaAnnotatedWith(Query.class);
    }

    static boolean ehSoltador(String consulta) {
        if (!ESCRITA_NA_COBRANCA.matcher(consulta).find()) {
            return false;
        }
        Matcher set = CLAUSULA_SET.matcher(consulta);
        while (set.find()) {
            String clausula = set.group(1);
            Matcher atribuicao = ATRIBUICAO_DE_STATUS.matcher(clausula);
            boolean atribuiu = false;
            while (atribuicao.find()) {
                atribuiu = true;
                if (!LITERAL_OPEN.matcher(atribuicao.group(1)).matches()) {
                    return true;
                }
            }
            if (!atribuiu && MENCAO_A_STATUS.matcher(clausula).find()) {
                return true;
            }
        }
        return false;
    }

    











    private static final Set<String> QUEM_PODE_SOLTAR = Set.of(
            origem(CheckoutTransactionService.class, "markAttemptNotCreated"),
            origem(PaymentReviewService.class, "review"));

    private static String origem(Class<?> classe, String metodo) {
        return classe.getName() + "#" + metodo;
    }

    private static String origemDe(JavaCodeUnit unidade) {
        return unidade.getOwner().getName() + "#" + unidade.getName();
    }

    









    private static ArchCondition<JavaClass> soltarCobrancaPendenteForaDe(Set<String> origensIsentas) {
        return new ArchCondition<JavaClass>(
                "levar uma cobrança pendente a NOT_CREATED fora da falha certa (K3) e da decisão humana (K5)") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getAccessesFromSelf().stream()
                        .filter(acesso -> acesso.getTarget().resolveMember()
                                .filter(JavaMethod.class::isInstance)
                                .map(alvo -> soltaCobrancaPendente((JavaMethod) alvo))
                                .orElse(false))
                        .filter(call -> !origensIsentas.contains(origemDe(call.getOrigin())))
                        .forEach(call -> events.add(SimpleConditionEvent.violated(call,
                                call.getOrigin().getFullName() + " chama ou referencia "
                                        + call.getTargetOwner().getSimpleName() + "."
                                        + call.getTarget().getName() + "() — leva uma cobrança"
                                        + " PENDING_CONFIRMATION a NOT_CREATED, o que LIBERA nova chamada"
                                        + " ao fornecedor. Só a falha certa (K3,"
                                        + " CheckoutTransactionService.markAttemptNotCreated) e a decisão"
                                        + " humana (K5, PaymentReviewService.review) soltam uma cobrança"
                                        + " pendente; um terceiro escritor é o retry automático sem humano"
                                        + " (ADR 0016 §3.10). Onde: " + call.getDescription())));
            }
        };
    }

    

    private static final Set<String> CAMPOS_DO_ARTEFATO_DA_COBRANCA = Set.of("txid", "pixCopyPaste");

    














    private static ArchCondition<JavaClass> escreverCampoDaCobrancaEmJava(
            Class<?> cobranca, Set<String> campos, boolean construtorDaK1Isento) {
        String dona = cobranca.getName();
        String nomes = campos.stream().sorted().collect(Collectors.joining("/"));
        return new ArchCondition<JavaClass>(
                "escrever " + cobranca.getSimpleName() + "." + nomes
                        + " em Java — a cobrança só muda por UPDATE condicional no banco") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getFieldAccessesFromSelf().stream()
                        .filter(acesso -> acesso.getAccessType() == JavaFieldAccess.AccessType.SET)
                        .filter(acesso -> acesso.getTargetOwner().getName().equals(dona))
                        .filter(acesso -> campos.contains(acesso.getTarget().getName()))
                        .filter(acesso -> !(construtorDaK1Isento
                                && acesso.getTarget().getName().equals("status")
                                && ehOConstrutorDaK1(acesso.getOrigin(), dona)))
                        .forEach(acesso -> events.add(SimpleConditionEvent.violated(acesso,
                                acesso.getOrigin().getFullName() + " escreve " + cobranca.getSimpleName() + "."
                                        + acesso.getTarget().getName() + " — "
                                        + porQueNaoSeEscreveEmJava(acesso.getTarget().getName())
                                        + " Onde: " + acesso.getDescription())));
            }
        };
    }

    
    private static boolean ehOConstrutorDaK1(JavaCodeUnit origem, String dona) {
        return origem.isConstructor()
                && origem.getOwner().getName().equals(dona)
                && origem.getRawParameterTypes().stream().map(JavaClass::getName).toList()
                        .equals(List.of(Order.class.getName()));
    }

    private static String porQueNaoSeEscreveEmJava(String campo) {
        if (campo.equals("status")) {
            return "o status da cobrança só muda por UPDATE … WHERE status = 'PENDING_CONFIRMATION' em"
                    + " PaymentRepository, com a contagem de linhas como decisão; fora disso, só o construtor"
                    + " da K1, Payment(Order), que a faz nascer PENDING_CONFIRMATION. Um mutador em Java"
                    + " chega ao banco por dirty checking, sem a condição: é o lost update que o QA mediu"
                    + " (ADR 0016 §2).";
        }
        return "a referência e o artefato só nascem na K2 — PaymentRepository.openWithCharge, UPDATE"
                + " condicional a PENDING_CONFIRMATION — e nunca em Java, nem no construtor (K1 nasce sem"
                + " artefato). Um escritor aqui é o Payment.attachCharge que a ADR 0016 §3.14 tirou da"
                + " entidade voltando por outro nome, e chegaria ao banco por dirty checking (pendência 11"
                + " da 0016 §6).";
    }

    



































































































































































    @ArchTest
    static final ArchRule soAFalhaCertaEADecisaoHumanaSoltamUmaCobrancaPendente = classes()
            .that(DescribedPredicate.not(CLASSE_DE_TESTE))
            .should(soltarCobrancaPendenteForaDe(QUEM_PODE_SOLTAR))
            .because("NOT_CREATED libera nova chamada ao fornecedor: quem o escreve decide se há retry,"
                    + " e retry sem humano é o que a decisão do Pedro de 07/10 proíbe (ADR 0016 §3.10)");

    @ArchTest
    static final ArchRule oStatusDaCobrancaNaoTemMutadorEmJava = classes()
            .should(escreverCampoDaCobrancaEmJava(Payment.class, Set.of("status"), true))
            .because("um mutador de status chegaria ao banco por dirty checking, num UPDATE sem a condição"
                    + " de PENDING_CONFIRMATION — o lost update que o QA mediu (ADR 0016 §2); e seria a"
                    + " porta por onde a regra acima deixaria de valer sem ficar vermelha");

    






    @ArchTest
    static final ArchRule oArtefatoDaCobrancaNaoTemEscritorEmJava = classes()
            .should(escreverCampoDaCobrancaEmJava(Payment.class, CAMPOS_DO_ARTEFATO_DA_COBRANCA, false))
            .because("txid e pix_copy_paste só nascem na K2 (PaymentRepository.openWithCharge, UPDATE condicional"
                    + " a PENDING_CONFIRMATION), sem isenção nenhuma — K1 nasce sem artefato. Um escritor em"
                    + " Java é o Payment.attachCharge que a ADR 0016 §3.14 tirou da entidade voltando por outro"
                    + " nome, e chegaria ao banco por dirty checking (pendência 11 da 0016 §6)");

    

    private static final Set<String> PACOTES_DE_SEGUNDO_MOTOR = Set.of(
            "org.springframework.jdbc",
            "jakarta.persistence.criteria",
            "org.hibernate.query");

    


    private static final Set<String> CLASSES_DE_SEGUNDO_MOTOR = Set.of(
            "jakarta.persistence.EntityManager",
            "jakarta.persistence.EntityManagerFactory",
            "jakarta.persistence.Query",
            "jakarta.persistence.TypedQuery",
            "jakarta.persistence.StoredProcedureQuery",
            "jakarta.persistence.NamedQuery",
            "jakarta.persistence.NamedQueries",
            "jakarta.persistence.NamedNativeQuery",
            "jakarta.persistence.NamedNativeQueries",
            "org.hibernate.Session",
            "org.hibernate.StatelessSession",
            "org.hibernate.SessionFactory",
            "org.springframework.data.jpa.repository.query.Procedure",
            "java.sql.Connection",
            "java.sql.Statement",
            "java.sql.PreparedStatement",
            "javax.sql.DataSource");

    






    private static final Set<String> QUEM_PODE_FALAR_SQL_FORA_DO_REPOSITORIO = Set.of();

    private static final DescribedPredicate<JavaClass> SEGUNDO_MOTOR_DE_SQL = describe(
            "um segundo motor de SQL/JPQL (JdbcTemplate, EntityManager, criteria, @NamedQuery, Session, Connection…)",
            javaClass -> CLASSES_DE_SEGUNDO_MOTOR.contains(javaClass.getName())
                    || PACOTES_DE_SEGUNDO_MOTOR.stream().anyMatch(p -> dentroDe(javaClass.getPackageName(), p)));

    private static final DescribedPredicate<JavaClass> PRODUCAO_SEM_ISENCAO_DE_SQL = DescribedPredicate
            .not(CLASSE_DE_TESTE)
            .and(describe("fora de QUEM_PODE_FALAR_SQL_FORA_DO_REPOSITORIO",
                    javaClass -> !QUEM_PODE_FALAR_SQL_FORA_DO_REPOSITORIO.contains(javaClass.getName())));

    
    private static ArchRule semSegundoMotorDeSqlEm(DescribedPredicate<JavaClass> escopo) {
        return noClasses()
                .that(escopo)
                .should().dependOnClassesThat(SEGUNDO_MOTOR_DE_SQL)
                .because("a regra 1 lê o texto das consultas declaradas em anotação de repositório; esta"
                        + " garante que em produção não há outro lugar onde um UPDATE possa nascer —"
                        + " sem ela, JdbcTemplate/EntityManager ficam fora 'por construção' e a promessa"
                        + " 'JPQL ou nativo' é mais larga que a regra (RV-4, ADR 0016 §3.10)");
    }

    @ArchTest
    static final ArchRule producaoSoFalaComOBancoPorRepositorioSpringData =
            semSegundoMotorDeSqlEm(PRODUCAO_SEM_ISENCAO_DE_SQL);

    

















































    private static final ArchCondition<JavaClass> CONSTROI_ILLEGAL_ARGUMENT =
            new ArchCondition<JavaClass>(
                    "construir IllegalArgumentException — recusa deliberada de service lança"
                            + " DomainRuleException pela origem (ADR 0019)") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getConstructorCallsFromSelf().stream()
                            .filter(call -> call.getTargetOwner().isAssignableTo(IllegalArgumentException.class))
                            .forEach(call -> events.add(SimpleConditionEvent.violated(call,
                                    call.getOrigin().getFullName() + " constrói "
                                            + call.getTargetOwner().getSimpleName()
                                            + " — recusa deliberada de service lança DomainRuleException"
                                            + " (ErrorCode.DOMAIN_RULE_VIOLATION com a mesma mensagem), não"
                                            + " IllegalArgumentException: depender do catch largo de"
                                            + " handleDomainRule prende o handler como canal de contrato"
                                            + " (ADR 0019 §3). Invariante de entidade e guarda de subida"
                                            + " continuam em IllegalArgument, mas não moram em ..service..."
                                            + " Onde: " + call.getDescription())));
                }
            };

    @ArchTest
    static final ArchRule recusaDeServiceNaoConstroiIllegalArgument = classes()
            .that().resideInAPackage("..service..")
            .should(CONSTROI_ILLEGAL_ARGUMENT)
            .because("recusa deliberada num service é contrato de resposta; depender do catch largo de"
                    + " IllegalArgumentException em handleDomainRule congela o handler como canal de"
                    + " contrato (a DV8 mediu o custo disso). A régua nova (06/09) lança"
                    + " DomainRuleException pela ORIGEM, e os quatro usos migraram na DV5 (ADR 0019 §3)");

    private static boolean ehFornecedor(String nomeDaClasse) {
        return CLASSES_DE_FORNECEDOR.contains(nomeDaClasse)
                || PACOTES_DE_FORNECEDOR.stream().anyMatch(p -> dentroDe(nomeDaClasse, p));
    }

    
    private static boolean dentroDe(String nome, String prefixo) {
        return nome.equals(prefixo) || nome.startsWith(prefixo + ".");
    }

    









    







    @Test
    void aRegraDeSaidaAcusaOAdaptadorRealQuandoAIsencaoDaZonaSaiDeCena() {
        JavaClasses adaptador = new ClassFileImporter().importClasses(R2StorageService.class);

        assertThatThrownBy(() -> classes().should(DEPENDER_DE_FORNECEDOR).check(adaptador))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("R2StorageService")
                .hasMessageContaining("software.amazon.awssdk");
    }

    




    @Test
    void aRegraDeSaidaNaoAcusaServicoQueNaoFalaComFornecedor() {
        JavaClasses servicosDeDentro =
                new ClassFileImporter().importClasses(CartService.class, ProductService.class);

        assertThatCode(() -> classes().should(DEPENDER_DE_FORNECEDOR).check(servicosDeDentro))
                .doesNotThrowAnyException();
    }

    





    @Test
    void aRegraDeSaidaAcusaClienteHttpEmServicoDeAplicacao() {
        JavaClasses fixture =
                new ClassFileImporter().importClasses(ServicoQueChamaOGatewayNaMao.class);

        assertThatThrownBy(() -> classes().should(DEPENDER_DE_FORNECEDOR).check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServicoQueChamaOGatewayNaMao")
                .hasMessageContaining("org.springframework.web.client.RestClient");
    }

    @Test
    void aRegraDaPortaAcusaInjecaoDeAdaptadorConcreto() {
        JavaClasses fixture =
                new ClassFileImporter().importClasses(ServicoQueInjetaOAdaptador.class);

        assertThatThrownBy(() -> classes().should(DEPENDER_DE_ADAPTADOR_CONCRETO).check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServicoQueInjetaOAdaptador")
                .hasMessageContaining("R2StorageService");
    }

    
    @Test
    void aRegraDaPortaAceitaInjecaoPelaInterface() {
        JavaClasses fixture =
                new ClassFileImporter().importClasses(ServicoQueDependeDaPorta.class);

        assertThatCode(() -> classes().should(DEPENDER_DE_ADAPTADOR_CONCRETO).check(fixture))
                .doesNotThrowAnyException();
    }

    





    @Test
    void osDoisEscoposSeparamProducaoDeTesteComoADeclaracaoDiz() {
        JavaClasses amostra = new ClassFileImporter().importClasses(
                ProductService.class, RegraDependenciaTest.class, ContratoDeDependenciaTest.class);

        assertThat(ESCOPO_DA_REGRA_DA_PORTA.test(amostra.get(ProductService.class)))
                .as("serviço de produção fora da zona de integração — no escopo da regra da porta")
                .isTrue();
        assertThat(ESCOPO_DA_REGRA_DA_PORTA.test(amostra.get(ContratoDeDependenciaTest.class)))
                .as("classe de teste — fora do escopo da regra da porta, porque testar"
                        + " adaptador exige nomeá-lo (GatewayFalsoTest, T6)")
                .isFalse();
        assertThat(ESCOPO_DA_REGRA_DO_FORNECEDOR.test(amostra.get(ContratoDeDependenciaTest.class)))
                .as("a mesma classe de teste continua no escopo da regra do fornecedor —"
                        + " é o que mantém a suíte rodando sem rede")
                .isTrue();
    }

    






    @Test
    void aRegraDeSaidaAcusaAPonteQuandoAIsencaoDelaSaiDeCena() {
        JavaClasses ponte = new ClassFileImporter().importClasses(BancoPorVariavelTest.Ponte.class);

        assertThatThrownBy(() -> classes().should(DEPENDER_DE_FORNECEDOR).check(ponte))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("BancoPorVariavelTest$Ponte")
                .hasMessageContaining("java.net.Socket");
    }

    




    @Test
    void aIsencaoDaPonteNaoCobreMaisNinguem() {
        JavaClasses amostra = new ClassFileImporter().importClasses(
                BancoPorVariavelTest.Ponte.class, BancoPorVariavelTest.class,
                ContratoDeDependenciaTest.class, ProductService.class);

        assertThat(ESCOPO_DA_REGRA_DO_FORNECEDOR.test(amostra.get(BancoPorVariavelTest.Ponte.class)))
                .as("a Ponte, e só ela, fica fora do escopo da regra do fornecedor")
                .isFalse();
        assertThat(ESCOPO_DA_REGRA_DO_FORNECEDOR.test(amostra.get(BancoPorVariavelTest.class)))
                .as("o teste que usa a Ponte continua no escopo — a isenção não é do arquivo")
                .isTrue();
        assertThat(ESCOPO_DA_REGRA_DO_FORNECEDOR.test(amostra.get(ContratoDeDependenciaTest.class)))
                .as("outro teste continua no escopo — a isenção não é de \"teste\"")
                .isTrue();
        assertThat(ESCOPO_DA_REGRA_DO_FORNECEDOR.test(amostra.get(ProductService.class)))
                .as("produção fora da zona de integração continua no escopo")
                .isTrue();
    }

    




    







    @Test
    void aRegraDoFreteAcusaOCheckoutQuandoAIsencaoSaiDeCena() {
        JavaClasses checkout =
                new ClassFileImporter().importClasses(CheckoutTransactionService.class);

        assertThatThrownBy(() -> classes().should(CALCULAR_FRETE).check(checkout))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("CheckoutTransactionService")
                .hasMessageContaining("costFor");
    }

    





    @Test
    void aRegraDoFreteNaoAcusaALeituraDoPedidoQueNaoRecalcula() {
        JavaClasses leitura = new ClassFileImporter().importClasses(OrderService.class);

        assertThatCode(() -> classes().should(CALCULAR_FRETE).check(leitura))
                .doesNotThrowAnyException();
    }

    





    @Test
    void aRegraDoFreteAcusaQuemRecalculaOCongelado() {
        JavaClasses fixture =
                new ClassFileImporter().importClasses(ServicoQueRecalculaOFreteCongelado.class);

        assertThatThrownBy(() -> classes().should(CALCULAR_FRETE).check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServicoQueRecalculaOFreteCongelado")
                .hasMessageContaining("costFor");
    }

    





    @Test
    void oEscopoDoFreteDeixaTesteDeFreteDeFora() {
        JavaClasses amostra = new ClassFileImporter().importClasses(
                OrderService.class, CheckoutTransactionService.class, FreteTest.class);

        assertThat(ESCOPO_DA_REGRA_DO_FRETE.test(amostra.get(OrderService.class)))
                .as("leitura do pedido: produção, não é o checkout — no escopo")
                .isTrue();
        assertThat(ESCOPO_DA_REGRA_DO_FRETE.test(amostra.get(CheckoutTransactionService.class)))
                .as("o checkout congela o frete: é quem PODE calcular — fora do escopo")
                .isFalse();
        assertThat(ESCOPO_DA_REGRA_DO_FRETE.test(amostra.get(FreteTest.class)))
                .as("teste da regra do frete chama costFor de propósito — fora do escopo")
                .isFalse();
    }

    






    








    @Test
    void aRegraDaCobrancaPendenteAcusaOsDoisSoltadoresReaisQuandoAIsencaoSaiDeCena() {
        JavaClasses soltadores = new ClassFileImporter().importClasses(
                CheckoutTransactionService.class, PaymentReviewService.class, PaymentRepository.class);

        assertThatThrownBy(() -> classes().should(soltarCobrancaPendenteForaDe(Set.of())).check(soltadores))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated (2 times)")
                .hasMessageContaining("CheckoutTransactionService.markAttemptNotCreated(")
                .hasMessageContaining("PaymentRepository.markNotCreated()")
                .hasMessageContaining("PaymentReviewService.review(")
                .hasMessageContaining("PaymentRepository.markNotCreatedByReview()");
    }

    






    @Test
    void aRegraDaCobrancaPendenteNaoAcusaQuemSoLeACobranca() {
        JavaClasses leitores = new ClassFileImporter().importClasses(
                OrderExpiryService.class, OrderService.class, PaymentRepository.class);

        assertThatCode(() -> classes().should(soltarCobrancaPendenteForaDe(Set.of())).check(leitores))
                .doesNotThrowAnyException();
    }

    





    @Test
    void aRegraDaCobrancaPendenteAcusaODestravadorPorAgenda() {
        JavaClasses fixture = new ClassFileImporter().importClasses(
                ServicoQueDestravaPendentesPorAgenda.class, PaymentRepository.class);

        assertThatThrownBy(() -> classes().should(soltarCobrancaPendenteForaDe(QUEM_PODE_SOLTAR)).check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServicoQueDestravaPendentesPorAgenda")
                .hasMessageContaining("PaymentRepository.markNotCreatedByReview()");
    }

    








    @Test
    void osSoltadoresSaoDescobertosPeloTextoDaConsultaNaoPeloNome() {
        JavaClasses amostra = new ClassFileImporter().importClasses(
                PaymentRepository.class, RepositorioQueSoltaPorOutroNome.class);
        JavaClass real = amostra.get(PaymentRepository.class);
        JavaClass fixture = amostra.get(RepositorioQueSoltaPorOutroNome.class);

        assertThat(real.getMethods().stream()
                .filter(RegraDependenciaTest::soltaCobrancaPendente)
                .map(JavaMethod::getName))
                .as("os soltadores de PaymentRepository — K3 e K5, e nenhum outro")
                .containsExactlyInAnyOrder("markNotCreated", "markNotCreatedByReview");

        assertThat(soltaCobrancaPendente(fixture.getMethod("rebaixarTodasAsVelhas", Instant.class)))
                .as("JPQL com o literal NOT_CREATED e um nome qualquer").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("mudarStatus", UUID.class, PaymentStatus.class)))
                .as("status por parâmetro — serviria para NOT_CREATED").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("rebaixarEmSql", UUID.class)))
                .as("SQL nativo").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("abrirSemConferir", UUID.class)))
                .as("SET para OPEN, com PENDING_CONFIRMATION só no WHERE — K2, não soltador").isFalse();
        assertThat(soltaCobrancaPendente(fixture.getMethod("contarPendentes")))
                .as("leitura").isFalse();
    }

    





    @Test
    void aRegraDoMutadorAcusaOConstrutorDePaymentQuandoAIsencaoSaiDeCena() {
        JavaClasses payment = new ClassFileImporter().importClasses(Payment.class);

        assertThatThrownBy(() -> classes()
                .should(escreverCampoDaCobrancaEmJava(Payment.class, Set.of("status"), false)).check(payment))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Payment.<init>(Backend.BrechoAPI.domain.Order)")
                .hasMessageContaining("escreve Payment.status");
        assertThatCode(() -> classes()
                .should(escreverCampoDaCobrancaEmJava(Payment.class, Set.of("status"), true)).check(payment))
                .doesNotThrowAnyException();
    }

    













    @Test
    void aRegraDoArtefatoAcusaOAttachChargeQueVoltouEPoupaOConstrutorDaK1EOsOutrosCampos() {
        Class<?> fixture = CobrancaComOsMutadoresQueARegraProibe.class;
        JavaClasses cobranca = new ClassFileImporter().importClasses(fixture, Order.class);
        String nome = fixture.getSimpleName();

        assertThatThrownBy(() -> classes()
                .should(escreverCampoDaCobrancaEmJava(fixture, Set.of("status", "txid", "pixCopyPaste"), true))
                .check(cobranca))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated (4 times)")
                .hasMessageContaining("attachCharge(java.lang.String, java.lang.String) escreve " + nome + ".txid")
                .hasMessageContaining("attachCharge(java.lang.String, java.lang.String) escreve " + nome + ".pixCopyPaste")
                .hasMessageContaining("setStatus(Backend.BrechoAPI.domain.enums.PaymentStatus) escreve " + nome + ".status")
                .hasMessageContaining("<init>(Backend.BrechoAPI.domain.Order, Backend.BrechoAPI.domain.enums.PaymentStatus)"
                        + " escreve " + nome + ".status")
                .satisfies(erro -> assertThat(erro.getMessage())
                        .as("gêmeos: o construtor da K1 e a escrita em updatedAt ficam fora")
                        .doesNotContain("<init>(Backend.BrechoAPI.domain.Order) escreve")
                        .doesNotContain("updatedAt"));

        assertThatThrownBy(() -> classes()
                .should(escreverCampoDaCobrancaEmJava(fixture, CAMPOS_DO_ARTEFATO_DA_COBRANCA, false))
                .check(cobranca))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated (2 times)")
                .hasMessageContaining("escreve " + nome + ".txid")
                .hasMessageContaining("escreve " + nome + ".pixCopyPaste")
                .satisfies(erro -> assertThat(erro.getMessage())
                        .as("a regra 4 não fala de status")
                        .doesNotContain("escreve " + nome + ".status"));
    }

    









    @Test
    void asSeteGrafiasQueORevisorMediuPassandoSaoSoltadoresEOsGemeosContinuamFora() {
        JavaClass fixture = new ClassFileImporter()
                .importClasses(RepositorioQueSoltaComGrafiasQueARegraNaoVia.class)
                .get(RepositorioQueSoltaComGrafiasQueARegraNaoVia.class);

        assertThat(soltaCobrancaPendente(fixture.getMethod("entidadeQualificada", UUID.class)))
                .as("1. JPQL com a entidade qualificada (update Backend.BrechoAPI.domain.Payment p)").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("tabelaComSchema", UUID.class)))
                .as("2. nativo com schema (UPDATE public.payments)").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("tabelaEntreAspas", UUID.class)))
                .as("3. nativo com a tabela entre aspas (UPDATE \"payments\")").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("colunaEntreAspas", UUID.class)))
                .as("4. nativo com a coluna entre aspas (SET \"status\" = 'NOT_CREATED')").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("setEmTupla", UUID.class)))
                .as("5. SET em tupla — status mencionado sem atribuição legível: dúvida acusa").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("parametroQueTerminaEmOpen", UUID.class, PaymentStatus.class)))
                .as("6. parâmetro cujo nome termina em OPEN (:novoOPEN)").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("porNativeQuery", UUID.class)))
                .as("7. @NativeQuery (meta-anotada com @Query)").isTrue();
        assertThat(soltaCobrancaPendente(fixture.getMethod("porUpsert", UUID.class)))
                .as("8. INSERT … ON CONFLICT DO UPDATE SET status = 'NOT_CREATED' (não era do Revisor;"
                        + " cai pelo mesmo alvo)").isTrue();

        assertThat(soltaCobrancaPendente(fixture.getMethod("abrirComSchemaEAspas", UUID.class)))
                .as("gêmeo: K2 com schema e coluna entre aspas — SET \"status\" = 'OPEN'").isFalse();
        assertThat(soltaCobrancaPendente(fixture.getMethod("vencerPedidosSemCobrancaViva")))
                .as("gêmeo: escrita em orders que só menciona a cobrança no WHERE").isFalse();
        assertThat(soltaCobrancaPendente(fixture.getMethod("contarPendentesEmSql")))
                .as("gêmeo: leitura por @NativeQuery").isFalse();
    }

    






    @Test
    void aRegraDaCobrancaPendenteAcusaAReferenciaDeMetodo() {
        JavaClasses fixture = new ClassFileImporter().importClasses(
                ServicoQueDestravaPorReferenciaDeMetodo.class, PaymentRepository.class);

        assertThatThrownBy(() -> classes().should(soltarCobrancaPendenteForaDe(QUEM_PODE_SOLTAR)).check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServicoQueDestravaPorReferenciaDeMetodo")
                .hasMessageContaining("references method")
                .hasMessageContaining("PaymentRepository.markNotCreated()");
    }

    






    @Test
    void aRegraDoSegundoMotorAcusaOSoltadorPorJdbcENaoAcusaQuemSoUsaORepositorio() {
        JavaClasses porJdbc = new ClassFileImporter().importClasses(ServicoQueDestravaPorJdbc.class);
        JavaClasses porRepositorio = new ClassFileImporter().importClasses(
                PaymentRepository.class, CheckoutTransactionService.class, PaymentReviewService.class);

        assertThatThrownBy(() -> semSegundoMotorDeSqlEm(DescribedPredicate.alwaysTrue()).check(porJdbc))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServicoQueDestravaPorJdbc")
                .hasMessageContaining("JdbcTemplate");
        assertThatCode(() -> semSegundoMotorDeSqlEm(DescribedPredicate.alwaysTrue()).check(porRepositorio))
                .doesNotThrowAnyException();
    }

    







    @Test
    void aRegraDaRecusaAcusaAConstrucaoDeIllegalArgument() {
        JavaClasses fixture =
                new ClassFileImporter().importClasses(ServicoQueRecusaComIllegalArgument.class);

        assertThatThrownBy(() -> classes().should(CONSTROI_ILLEGAL_ARGUMENT).check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ServicoQueRecusaComIllegalArgument")
                .hasMessageContaining("IllegalArgumentException");
    }

    






    @Test
    void aRegraDaRecusaNaoAcusaOServiceJaMigrado() {
        JavaClasses migrado = new ClassFileImporter().importClasses(ProductService.class);

        assertThatCode(() -> classes().should(CONSTROI_ILLEGAL_ARGUMENT).check(migrado))
                .doesNotThrowAnyException();
    }

    




    @SuppressWarnings("unused")
    static final class ServicoQueChamaOGatewayNaMao {
        private final RestClient http = RestClient.create();

        String cobrar(String pedido) {
            return http.get().uri("https://api.abacatepay.com/v1/pixQrCode/{id}", pedido)
                    .retrieve().body(String.class);
        }
    }

    
    @SuppressWarnings("unused")
    static final class ServicoQueInjetaOAdaptador {
        private final R2StorageService adaptador;

        ServicoQueInjetaOAdaptador(R2StorageService adaptador) {
            this.adaptador = adaptador;
        }
    }

    
    @SuppressWarnings("unused")
    static final class ServicoQueDependeDaPorta {
        private final StorageService porta;

        ServicoQueDependeDaPorta(StorageService porta) {
            this.porta = porta;
        }
    }

    



    @SuppressWarnings("unused")
    static final class ServicoQueRecalculaOFreteCongelado {
        private final ShippingService frete;

        ServicoQueRecalculaOFreteCongelado(ShippingService frete) {
            this.frete = frete;
        }

        BigDecimal totalDaRetomada(UfBrasileira uf, BigDecimal subtotal) {
            return subtotal.add(frete.costFor(uf));
        }
    }

    




    @SuppressWarnings("unused")
    static final class ServicoQueDestravaPendentesPorAgenda {
        private final PaymentRepository cobrancas;

        ServicoQueDestravaPendentesPorAgenda(PaymentRepository cobrancas) {
            this.cobrancas = cobrancas;
        }

        void destravar(List<UUID> pendentesVelhas, UUID robo, Instant agora) {
            for (UUID id : pendentesVelhas) {
                cobrancas.markNotCreatedByReview(id, robo, agora, agora);
            }
        }
    }

    




    @SuppressWarnings("unused")
    interface RepositorioQueSoltaPorOutroNome {

        @Modifying
        @Query("update Payment p set p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.NOT_CREATED"
                + " where p.createdAt < :antes")
        int rebaixarTodasAsVelhas(@Param("antes") Instant antes);

        @Modifying
        @Query("update Payment p set p.status = :novo where p.id = :id")
        int mudarStatus(@Param("id") UUID id, @Param("novo") PaymentStatus novo);

        @Modifying
        @Query(value = "UPDATE payments SET status = 'NOT_CREATED' WHERE id = :id", nativeQuery = true)
        int rebaixarEmSql(@Param("id") UUID id);

        @Modifying
        @Query("update Payment p set p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.OPEN"
                + " where p.id = :id"
                + " and p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.PENDING_CONFIRMATION")
        int abrirSemConferir(@Param("id") UUID id);

        @Query("select count(p) from Payment p"
                + " where p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.PENDING_CONFIRMATION")
        long contarPendentes();
    }

    




    @SuppressWarnings("unused")
    interface RepositorioQueSoltaComGrafiasQueARegraNaoVia {

        @Modifying
        @Query("update Backend.BrechoAPI.domain.Payment p"
                + " set p.status = Backend.BrechoAPI.domain.enums.PaymentStatus.NOT_CREATED where p.id = :id")
        int entidadeQualificada(@Param("id") UUID id);

        @Modifying
        @Query(value = "UPDATE public.payments SET status = 'NOT_CREATED' WHERE id = :id", nativeQuery = true)
        int tabelaComSchema(@Param("id") UUID id);

        @Modifying
        @Query(value = "UPDATE \"payments\" SET status = 'NOT_CREATED' WHERE id = :id", nativeQuery = true)
        int tabelaEntreAspas(@Param("id") UUID id);

        @Modifying
        @Query(value = "UPDATE payments SET \"status\" = 'NOT_CREATED' WHERE id = :id", nativeQuery = true)
        int colunaEntreAspas(@Param("id") UUID id);

        @Modifying
        @Query(value = "UPDATE payments SET (status, updated_at) = ('NOT_CREATED', now()) WHERE id = :id",
                nativeQuery = true)
        int setEmTupla(@Param("id") UUID id);

        @Modifying
        @Query("update Payment p set p.status = :novoOPEN where p.id = :id")
        int parametroQueTerminaEmOpen(@Param("id") UUID id, @Param("novoOPEN") PaymentStatus novoOPEN);

        @Modifying
        @NativeQuery("UPDATE payments SET status = 'NOT_CREATED' WHERE id = :id")
        int porNativeQuery(@Param("id") UUID id);

        @Modifying
        @Query(value = "INSERT INTO payments (id, status) VALUES (:id, 'PENDING_CONFIRMATION')"
                + " ON CONFLICT (id) DO UPDATE SET status = 'NOT_CREATED'", nativeQuery = true)
        int porUpsert(@Param("id") UUID id);

        @Modifying
        @Query(value = "UPDATE public.payments SET \"status\" = 'OPEN', updated_at = now()"
                + " WHERE id = :id AND status = 'PENDING_CONFIRMATION'", nativeQuery = true)
        int abrirComSchemaEAspas(@Param("id") UUID id);

        @Modifying
        @Query("update Order o set o.status = Backend.BrechoAPI.domain.enums.OrderStatus.EXPIRED"
                + " where o.id not in (select p.order.id from Payment p"
                + " where p.status <> Backend.BrechoAPI.domain.enums.PaymentStatus.NOT_CREATED)")
        int vencerPedidosSemCobrancaViva();

        @NativeQuery("SELECT count(*) FROM payments WHERE status = 'PENDING_CONFIRMATION'")
        long contarPendentesEmSql();
    }

    




    @SuppressWarnings("unused")
    static final class ServicoQueDestravaPorReferenciaDeMetodo {
        private final PaymentRepository cobrancas;

        ServicoQueDestravaPorReferenciaDeMetodo(PaymentRepository cobrancas) {
            this.cobrancas = cobrancas;
        }

        void destravar(List<UUID> pendentesVelhas, Instant agora) {
            BiFunction<UUID, Instant, Integer> soltar = cobrancas::markNotCreated;
            pendentesVelhas.forEach(id -> soltar.apply(id, agora));
        }
    }

    




    @SuppressWarnings("unused")
    static final class ServicoQueDestravaPorJdbc {
        private final JdbcTemplate jdbc;

        ServicoQueDestravaPorJdbc(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        int destravar(Instant antes) {
            return jdbc.update("update payments set status = 'NOT_CREATED'"
                    + " where status = 'PENDING_CONFIRMATION' and created_at < ?", antes);
        }
    }

    






    @SuppressWarnings("unused")
    static final class CobrancaComOsMutadoresQueARegraProibe {
        private PaymentStatus status;
        private String txid;
        private String pixCopyPaste;
        private Instant updatedAt;

        CobrancaComOsMutadoresQueARegraProibe(Order order) {
            this.status = PaymentStatus.PENDING_CONFIRMATION;
        }

        CobrancaComOsMutadoresQueARegraProibe(Order order, PaymentStatus status) {
            this.status = status;
        }

        void attachCharge(String txid, String copyPaste) {
            this.txid = txid;
            this.pixCopyPaste = copyPaste;
        }

        void setStatus(PaymentStatus status) {
            this.status = status;
        }

        void touch(Instant now) {
            this.updatedAt = now;
        }
    }

    









    @SuppressWarnings("unused")
    static final class ServicoQueRecusaComIllegalArgument {
        void recusar(String uf) {
            throw new IllegalArgumentException("uf inválida: " + uf);
        }
    }
}
