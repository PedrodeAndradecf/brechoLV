package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Order;
import Backend.BrechoAPI.domain.enums.OrderStatus;
import Backend.BrechoAPI.domain.enums.PaymentStatus;
import Backend.BrechoAPI.repository.OrderRepository;
import Backend.BrechoAPI.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
















































@Service
@RequiredArgsConstructor
public class OrderExpiryService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    


















































    @Transactional
    public int expireDueOf(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId é obrigatório");
        Objects.requireNonNull(now, "now é obrigatório");

        Order aberto = orderRepository
                .findByUserIdAndStatus(userId, OrderStatus.AWAITING_PAYMENT)
                .orElse(null);

        
        
        
        if (aberto == null || !aberto.isDueAt(now)) {
            return 0;
        }

        
        
        
        if (paymentRepository.existsByOrderIdAndStatus(aberto.getId(),
                PaymentStatus.PENDING_CONFIRMATION)) {
            return 0;
        }

        aberto.expire();
        orderRepository.flush();
        return 1;
    }
}
