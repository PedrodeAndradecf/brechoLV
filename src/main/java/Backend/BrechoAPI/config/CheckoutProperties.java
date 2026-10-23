package Backend.BrechoAPI.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;





























@ConfigurationProperties("checkout")
public record CheckoutProperties(Duration orderTtl) {

    public CheckoutProperties {
        if (orderTtl == null) {
            throw new IllegalArgumentException(
                    "checkout.order-ttl não está configurado — sem prazo o pedido nunca vence e o"
                            + " cliente que não pagar fica travado para sempre (decisão 8)");
        }
        if (orderTtl.isZero() || orderTtl.isNegative()) {
            throw new IllegalArgumentException(
                    "checkout.order-ttl precisa ser positivo; veio " + orderTtl
                            + " — prazo não positivo faz o pedido nascer vencido e nenhum caminho"
                            + " aceita pagá-lo");
        }
    }
}
