package Backend.BrechoAPI.projection;

import java.math.BigDecimal;
import java.util.UUID;







public interface ProductCardProjection {

    UUID getId();

    String getTitle();

    String getBrand();

    String getSize();

    String getColor();

    BigDecimal getPrice();

    String getCoverStorageKey();
}
