package Backend.BrechoAPI.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;





























































@ConfigurationProperties("shipping")
public record ShippingProperties(Map<String, BigDecimal> byUf) {

    
    private static final int ESCALA_DO_DINHEIRO = 2;

    public ShippingProperties {
        
        
        
        Map<String, BigDecimal> normalizado = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (byUf != null) {
            byUf.forEach((uf, custo) -> normalizado.put(uf, comEscalaDeDinheiro(uf, custo)));
        }
        byUf = Collections.unmodifiableMap(normalizado);
    }

    











    private static BigDecimal comEscalaDeDinheiro(String uf, BigDecimal custo) {
        try {
            return custo.setScale(ESCALA_DO_DINHEIRO);
        } catch (ArithmeticException e) {
            throw new IllegalStateException(
                    "shipping.by-uf[" + uf + "]=" + custo.toPlainString() + " tem mais de duas"
                            + " casas decimais: frete é dinheiro e a coluna guarda numeric(10,2)."
                            + " Corrija a tabela — arredondar aqui mudaria o valor em silêncio", e);
        }
    }
}
