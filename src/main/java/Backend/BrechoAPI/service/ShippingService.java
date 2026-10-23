package Backend.BrechoAPI.service;

import Backend.BrechoAPI.config.ShippingProperties;
import Backend.BrechoAPI.domain.enums.UfBrasileira;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;



























@Service
public class ShippingService {

    private final ShippingProperties properties;

    public ShippingService(ShippingProperties properties) {
        this.properties = properties;
    }

    







    public BigDecimal costFor(UfBrasileira uf) {
        BigDecimal custo = properties.byUf().get(uf.name());
        if (custo == null) {
            throw new DomainRuleException(ErrorCode.SHIPPING_UNAVAILABLE_FOR_UF,
                    "não entregamos em " + uf.name());
        }
        return custo;
    }
}
