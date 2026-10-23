package Backend.BrechoAPI.security;

import Backend.BrechoAPI.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;







@Component("productSecurity")
@RequiredArgsConstructor
public class ProductSecurity {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public boolean isOwner(UUID productId, Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails user)) {
            return false;
        }
        return productRepository
                .findByIdAndSellerIdAndDeletedAtIsNull(productId, user.getId())
                .isPresent();
    }
}
