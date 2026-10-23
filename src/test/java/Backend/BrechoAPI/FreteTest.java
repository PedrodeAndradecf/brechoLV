package Backend.BrechoAPI;

import Backend.BrechoAPI.config.ShippingProperties;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.service.ShippingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;


































@SpringJUnitConfig(FreteTest.ContextoDeFrete.class)
@TestPropertySource(properties = {
        
        
        
        "shipping.by-uf[SP]=19.90",
        "shipping.by-uf[RJ]=24.90"
})
class FreteTest {

    









































































































































































































    








    @Configuration
    @EnableConfigurationProperties(ShippingProperties.class)
    @Import(ShippingService.class)
    static class ContextoDeFrete {
    }

    @Autowired
    private ShippingService shippingService;

    





    private static ApplicationContextRunner contextoCom(String... propriedades) {
        return new ApplicationContextRunner()
                .withUserConfiguration(ContextoDeFrete.class)
                .withPropertyValues(propriedades);
    }

    

    















    @Test
    void ufNoMapaDevolveOValorConfigurado() {
        BigDecimal custo = shippingService.costFor(UfBrasileira.SP);

        assertThat(custo)
                .as("SP está no mapa deste teste valendo 19.90 — se vier outra coisa, "
                        + "ou o mapa não foi ligado, ou a chave lida não é a configurada")
                .isNotNull()
                .isEqualByComparingTo("19.90");
        assertThat(custo.toPlainString())
                .as("escala 2 é centavo: 19.9 no lugar de 19.90 é sinal de que o valor "
                        + "passou por double em algum ponto — frete é dinheiro, e a V3 guarda "
                        + "shipping_cost em numeric(10,2)")
                .isEqualTo("19.90");
    }

    






    @Test
    void cadaUfDoMapaDevolveOSeuProprioValor() {
        BigDecimal custoRj = shippingService.costFor(UfBrasileira.RJ);

        assertThat(custoRj)
                .as("RJ está no mapa deste teste valendo 24.90")
                .isEqualByComparingTo("24.90");
        assertThat(custoRj)
                .as("se RJ e SP saem iguais, o valor não veio da chave — veio de "
                        + "uma constante, e a tabela de frete não está sendo consultada")
                .isNotEqualByComparingTo(shippingService.costFor(UfBrasileira.SP));
    }

    

    













    @Test
    void ufForaDoMapaRecusaComCodigoProprio() {
        Throwable erro = catchThrowable(() -> shippingService.costFor(UfBrasileira.AC));

        assertThat(erro)
                .as("AC não está no mapa: a decisão de 05/09/2026 é recusar o checkout, "
                        + "não cobrar um frete padrão que ninguém escolheu")
                .isInstanceOf(DomainRuleException.class);
        assertThat(((DomainRuleException) erro).getCode())
                .as("o front precisa distinguir 'não entregamos nessa UF' de 'seu carrinho "
                        + "está vazio' — os dois são 422, e só o code os separa")
                .isEqualTo(ErrorCode.SHIPPING_UNAVAILABLE_FOR_UF);
        assertThat(erro.getMessage())
                .as("erro que não diz QUAL UF não é erro claro: a cliente precisa saber "
                        + "que é o endereço, e qual")
                .contains("AC");
    }

    

    
























    @Test
    void semNenhumaConfiguracaoDeFreteTodaUfEhRecusada() {
        contextoCom().run(contexto -> {
            ShippingService service = contexto.getBean(ShippingService.class);

            Throwable erro = catchThrowable(() -> service.costFor(UfBrasileira.SP));

            assertThat(erro)
                    .as("mapa ausente é o modo de falha mais caro de T6 justamente porque "
                            + "PARECE a regra funcionando; o que ele não pode virar é 500 — "
                            + "o Binder entrega byUf NULO, não mapa vazio")
                    .isInstanceOf(DomainRuleException.class);
            assertThat(((DomainRuleException) erro).getCode())
                    .isEqualTo(ErrorCode.SHIPPING_UNAVAILABLE_FOR_UF);
        });
    }

    

























    @Test
    void chaveEmMinusculaNaConfiguracaoEhAMesmaUf() {
        contextoCom("shipping.by-uf[sp]=19.90").run(contexto -> {
            ShippingService service = contexto.getBean(ShippingService.class);

            assertThat(service.costFor(UfBrasileira.SP))
                    .as("o Binder liga a chave literal \"sp\"; se o service só sabe ler "
                            + "\"SP\", uma minúscula na configuração tira São Paulo do ar "
                            + "sem nenhum sinal")
                    .isEqualByComparingTo("19.90");

            assertThat(catchThrowable(() -> service.costFor(UfBrasileira.RJ)))
                    .as("tolerar a caixa não é tolerar qualquer coisa: RJ não está neste "
                            + "mapa e continua recusado")
                    .isInstanceOf(DomainRuleException.class);
        });
    }

    











    @Test
    void freteZeroConfiguradoEhCobradoComoZeroNaoRecusado() {
        contextoCom("shipping.by-uf[DF]=0.00").run(contexto -> {
            ShippingService service = contexto.getBean(ShippingService.class);

            assertThat(service.costFor(UfBrasileira.DF))
                    .as("DF está no mapa valendo 0.00 — 'sem custo' não é 'sem frete'")
                    .isEqualByComparingTo("0.00");
        });
    }

    

    














    @Test
    void aTabelaDeFreteEhLigadaPorConfigurationPropertiesENaoPorValue() {
        ConfigurationProperties anotacao =
                AnnotationUtils.findAnnotation(ShippingProperties.class, ConfigurationProperties.class);

        assertThat(anotacao)
                .as("sem @ConfigurationProperties não há Binder, e sem Binder não há mapa")
                .isNotNull();
        assertThat(List.of(anotacao.prefix(), anotacao.value()))
                .as("o prefixo é 'shipping': é ele que casa com shipping.by-uf[UF] no "
                        + "application.properties")
                .contains("shipping");

        semNenhumValue(ShippingProperties.class);
        semNenhumValue(ShippingService.class);
    }

    








    @Test
    void oValorDoFreteEhBigDecimalNoTipoDoMapa() throws Exception {
        Field campo = ShippingProperties.class.getDeclaredField("byUf");

        assertThat(campo.getGenericType())
                .as("byUf precisa ser um tipo parametrizado — Map cru esconde o tipo do valor")
                .isInstanceOf(ParameterizedType.class);

        ParameterizedType tipo = (ParameterizedType) campo.getGenericType();
        assertThat(tipo.getRawType())
                .as("a tabela de frete é um mapa UF -> custo")
                .isEqualTo(Map.class);
        assertThat(tipo.getActualTypeArguments()[0])
                .as("a chave é a sigla da UF como texto; o Binder não converte chave de mapa "
                        + "para enum sem conversor, e um enum aqui recusaria a configuração "
                        + "inteira por causa de uma sigla errada")
                .isEqualTo(String.class);
        assertThat(tipo.getActualTypeArguments()[1])
                .as("dinheiro é BigDecimal — Double no mapa é o erro de arredondamento "
                        + "entrando pela porta da frente")
                .isEqualTo(BigDecimal.class);
    }

    
    private static void semNenhumValue(Class<?> classe) {
        for (Field campo : classe.getDeclaredFields()) {
            assertThat(campo.getAnnotation(Value.class))
                    .as("@Value no campo %s.%s — @Value não liga Map, e a falha é silenciosa "
                            + "(armadilha 10)", classe.getSimpleName(), campo.getName())
                    .isNull();
        }
        for (Constructor<?> construtor : classe.getDeclaredConstructors()) {
            for (Parameter parametro : construtor.getParameters()) {
                assertThat(parametro.getAnnotation(Value.class))
                        .as("@Value em parâmetro do construtor de %s", classe.getSimpleName())
                        .isNull();
            }
        }
    }
}
