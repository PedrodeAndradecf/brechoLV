package Backend.BrechoAPI.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
































public interface PaymentGateway {

    





























    PixCharge createPixCharge(UUID orderId, BigDecimal amount, Instant expiresAt);

    






    record PixCharge(String txid, String copyPaste) {}
}
