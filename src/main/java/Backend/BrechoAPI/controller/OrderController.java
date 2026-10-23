package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.dto.request.CheckoutRequestDTO;
import Backend.BrechoAPI.dto.response.OrderResponseDTO;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.CheckoutService;
import Backend.BrechoAPI.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;














@RestController
@RequestMapping("/me/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;

    













    @PostMapping
    public ResponseEntity<OrderResponseDTO> checkout(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody CheckoutRequestDTO request) {

        CheckoutService.CheckoutResult resultado =
                checkoutService.checkout(user.getId(), request.addressId());

        return ResponseEntity
                .status(resultado.resumed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(resultado.order());
    }

    















    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> read(
            @AuthenticationPrincipal CustomUserDetails user,
            @PathVariable UUID id) {

        return ResponseEntity.ok(orderService.findOwn(user.getId(), id));
    }
}
