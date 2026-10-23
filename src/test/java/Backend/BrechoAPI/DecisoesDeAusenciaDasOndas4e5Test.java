package Backend.BrechoAPI;

import Backend.BrechoAPI.config.CheckoutProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

































































@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DecisoesDeAusenciaDasOndas4e5Test {

    






































































    @DynamicPropertySource
    static void jwtSecret(DynamicPropertyRegistry registry) {
        registry.add("jwt.secret", () -> "h+QhfS2wVeXhPJp2/5C/ay1Jdi4b6quGoVGIDBOXgL8=");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationContext context;

    

    











    @Test
    void paymentsNaoTemColunaMethod() {
        assertThat(contarColunas("payments", "method"))
                .as("D6: payments não tem 'method' — enum de um valor só é capacidade "
                        + "sem leitor (Regra 1). A V3 registra a decisão; esta query confere "
                        + "o catálogo, não a prosa")
                .isZero();

        
        
        
        assertThat(contarColunas("payments", "txid"))
                .as("gêmeo por presença: payments.txid existe (V3) — se esta contagem "
                        + "também desse zero, o nome da tabela estaria errado e a asserção de "
                        + "ausência acima seria falso-verde")
                .isOne();
    }

    

    






    @Test
    void commissionRateNaoExisteEmLugarNenhum() {
        assertThat(contarColunasPorNome("commission_rate"))
                .as("D7: commission_rate é Fase 4, sem escritor nem leitor hoje — nenhuma "
                        + "tabela do schema public pode tê-la")
                .isZero();

        
        
        
        assertThat(contarColunasPorNome("txid"))
                .as("gêmeo por presença: 'txid' existe no schema (payments) — prova que a "
                        + "busca por nome de coluna enxerga o catálogo")
                .isOne();
    }

    

    




















    @Test
    void nenhumBeanDaAplicacaoTemMetodoScheduled() {
        List<String> comScheduled = beansDaAplicacaoComScheduled();

        assertThat(comScheduled)
                .as("D10: não há @Scheduled nesta fase (decisão 10) — o job de expiração "
                        + "é pendência com gatilho da Fase 4. Beans encontrados com @Scheduled: %s",
                        comScheduled)
                .isEmpty();

        
        assertThat(temMetodoScheduled(FixtureComScheduled.class))
                .as("gêmeo por presença: o detector acusa um @Scheduled num fixture — sem "
                        + "isto, 'nenhum bean tem @Scheduled' poderia ser verdade só porque o "
                        + "detector nunca enxerga anotação nenhuma")
                .isTrue();
    }

    















    @Test
    void enableSchedulingNaoEstaLigado() {
        assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class))
                .as("D10: @EnableScheduling registraria um ScheduledAnnotationBeanPostProcessor "
                        + "— ele não pode existir nesta fase (decisão 10)")
                .isEmpty();

        assertThat(context.containsBean(
                TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
                .as("D10: o bean que @EnableScheduling registra por nome fixo (%s) também não "
                        + "pode estar no contexto — o nome vem da constante do framework, não "
                        + "de um literal que poderia drift e dar falso-verde",
                        TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)
                .isFalse();

        
        
        assertThatCode(() -> Class.forName(
                "org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor"))
                .as("gêmeo por presença: o tipo existe no classpath — a asserção acima fala "
                        + "de ausência de bean, não de um nome de tipo que nunca existiu")
                .doesNotThrowAnyException();
    }

    

    








    @Test
    void comONomeCertoDaPropriedadeOContextoSobe() {
        contextoDeCheckout("checkout.order-ttl=PT30M").run(ctx -> {
            assertThat(ctx)
                    .as("checkout.order-ttl=PT30M é o nome e o valor certos — o contexto tem "
                            + "de subir")
                    .hasNotFailed();
            assertThat(ctx.getBean(CheckoutProperties.class).orderTtl())
                    .as("e o valor tem de chegar ligado ao orderTtl")
                    .isEqualTo(Duration.ofMinutes(30));
        });
    }

    






    @Test
    void semAPropriedadeOContextoNaoSobe() {
        contextoDeCheckout().run(ctx -> {
            assertThat(ctx)
                    .as("sem checkout.order-ttl o pedido nunca venceria e o cliente ficaria "
                            + "travado (decisão 8) — a subida tem de cair")
                    .hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("checkout.order-ttl");
        });
    }

    











    @Test
    void oNomeErradoReservationTtlNaoEnganaAGuarda() {
        contextoDeCheckout("checkout.reservation-ttl=PT30M").run(ctx -> {
            assertThat(ctx)
                    .as("reservation-ttl é o nome que a decisão 11 tirou de circulação; ele "
                            + "não liga orderTtl, então a subida tem de cair como se nada "
                            + "estivesse configurado")
                    .hasFailed();
            assertThat(ctx.getStartupFailure())
                    .as("e a falha nomeia a chave CERTA (checkout.order-ttl), não a errada — "
                            + "quem configurou reservation-ttl precisa descobrir o nome real")
                    .rootCause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("checkout.order-ttl");
        });
    }

    

    
    private int contarColunas(String tabela, String coluna) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = 'public' AND table_name = ? AND column_name = ?""",
                Integer.class, tabela, coluna);
        return n == null ? 0 : n;
    }

    
    private int contarColunasPorNome(String coluna) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                 WHERE table_schema = 'public' AND column_name = ?""",
                Integer.class, coluna);
        return n == null ? 0 : n;
    }

    




    private List<String> beansDaAplicacaoComScheduled() {
        List<String> achados = new ArrayList<>();
        for (String nome : context.getBeanDefinitionNames()) {
            Class<?> tipo = context.getType(nome);
            if (tipo == null) {
                continue;
            }
            Class<?> real = ClassUtils.getUserClass(tipo);
            if (!real.getName().startsWith("Backend.BrechoAPI")) {
                continue;
            }
            if (temMetodoScheduled(real)) {
                achados.add(nome + " (" + real.getName() + ")");
            }
        }
        return achados;
    }

    private static boolean temMetodoScheduled(Class<?> tipo) {
        for (Method m : ReflectionUtils.getUniqueDeclaredMethods(tipo)) {
            if (AnnotatedElementUtils.findMergedAnnotation(m, Scheduled.class) != null
                    || AnnotatedElementUtils.findMergedAnnotation(m, Schedules.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static ApplicationContextRunner contextoDeCheckout(String... propriedades) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ContextoDeCheckout.class)
                .withPropertyValues(propriedades);
    }

    





    @EnableConfigurationProperties(CheckoutProperties.class)
    static class ContextoDeCheckout {
    }

    




    static class FixtureComScheduled {
        @Scheduled(fixedRate = 1000)
        void jobDeMentira() {
        }
    }
}
