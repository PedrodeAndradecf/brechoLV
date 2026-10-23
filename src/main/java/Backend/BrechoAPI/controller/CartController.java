package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.dto.request.AddCartItemRequestDTO;
import Backend.BrechoAPI.dto.response.CartResponseDTO;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;









@RestController
@RequestMapping("/me/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponseDTO> getCart(@AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(cartService.getCart(user.getId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponseDTO> addItem(@AuthenticationPrincipal CustomUserDetails user,
                                                     @Valid @RequestBody AddCartItemRequestDTO request) {
        return ResponseEntity.ok(cartService.addItem(user.getId(), request.productId()));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<Void> removeItem(@AuthenticationPrincipal CustomUserDetails user,
                                            @PathVariable UUID productId) {
        cartService.removeItem(user.getId(), productId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items")
    public ResponseEntity<Void> clearItems(@AuthenticationPrincipal CustomUserDetails user) {
        cartService.clearItems(user.getId());
        return ResponseEntity.noContent().build();
    }
}
