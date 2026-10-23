package Backend.BrechoAPI.repository;

import Backend.BrechoAPI.domain.Order;
import Backend.BrechoAPI.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    
    
    
    
    
    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    














    Optional<Order> findByUserIdAndStatus(UUID userId, OrderStatus status);
}
