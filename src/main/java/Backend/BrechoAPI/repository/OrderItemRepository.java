package Backend.BrechoAPI.repository;

import Backend.BrechoAPI.domain.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    
































    List<OrderItem> findAllByOrderIdOrderByIdAsc(UUID orderId);
}
