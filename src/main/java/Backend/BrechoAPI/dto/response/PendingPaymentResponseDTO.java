package Backend.BrechoAPI.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;



















public record PendingPaymentResponseDTO(UUID paymentId, UUID orderId, BigDecimal amount,
                                        String orderNsu, Instant createdAt, String phone) {}
