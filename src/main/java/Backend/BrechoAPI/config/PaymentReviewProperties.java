package Backend.BrechoAPI.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
























@ConfigurationProperties("payment.review")
public record PaymentReviewProperties(Duration pendingAfter) {

    public PaymentReviewProperties {
        if (pendingAfter == null) {
            throw new IllegalArgumentException(
                    "payment.review.pending-after não está configurado — sem esse prazo a fila de"
                            + " exceção não tem limiar e a decisão humana não tem precondição. Defina"
                            + " payment.review.pending-after em application.properties com uma"
                            + " duração ISO-8601 positiva, por exemplo PT15M");
        }
        if (pendingAfter.isZero() || pendingAfter.isNegative()) {
            throw new IllegalArgumentException(
                    "payment.review.pending-after precisa ser positivo; veio " + pendingAfter
                            + " — X não positivo deixaria decidir sobre cobrança com chamada em voo");
        }
    }
}
