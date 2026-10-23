package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Cart;
import Backend.BrechoAPI.domain.CartItem;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.dto.response.CartItemResponseDTO;
import Backend.BrechoAPI.dto.response.CartResponseDTO;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.PersistenceIntegrityException;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.CartItemRepository;
import Backend.BrechoAPI.repository.CartRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;







@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    









    public CartResponseDTO getCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return new CartResponseDTO(null, List.of());
        }

        List<CartItem> cartItems = cartItemRepository.findAllByCartId(cart.getId());

        List<UUID> productIds = cartItems.stream().map(ci -> ci.getProduct().getId()).toList();
        Map<UUID, Product> productsById = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<CartItemResponseDTO> items = cartItems.stream()
                .map(ci -> toResponse(ci, productsById))
                .toList();

        return new CartResponseDTO(cart.getId(), items);
    }

    










    @Transactional
    public CartResponseDTO addItem(UUID userId, UUID productId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CART_NOT_FOUND, "carrinho não encontrado"));

        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "peça não encontrada"));

        if (cartItemRepository.findByCartIdAndProductId(cart.getId(), productId).isEmpty()) {
            cartItemRepository.save(CartItem.create(cart, product));
        }

        return getCart(userId);
    }

    




    @Transactional
    public void removeItem(UUID userId, UUID productId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return;
        }
        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
    }

    
    @Transactional
    public void clearItems(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart == null) {
            return;
        }
        cartItemRepository.deleteAllByCartId(cart.getId());
    }

    















    private CartItemResponseDTO toResponse(CartItem item, Map<UUID, Product> productsById) {
        Product product = productsById.get(item.getProduct().getId());
        if (product == null) {
            log.error("produto referenciado pelo item de carrinho não existe: {}",
                    item.getProduct().getId());
            throw PersistenceIntegrityException.leituraInconsistente();
        }

        return new CartItemResponseDTO(product.getId(), product.getTitle(), product.getPrice(), product.isArchived());
    }
}
