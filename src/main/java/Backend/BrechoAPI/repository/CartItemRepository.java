package Backend.BrechoAPI.repository;

import Backend.BrechoAPI.domain.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    
    
    
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);

    List<CartItem> findAllByCartId(UUID cartId);

    void deleteByCartIdAndProductId(UUID cartId, UUID productId);

    void deleteAllByCartId(UUID cartId);
}
