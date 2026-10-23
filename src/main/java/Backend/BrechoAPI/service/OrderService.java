package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Order;
import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.domain.ShippingSnapshot;
import Backend.BrechoAPI.domain.enums.OrderStatus;
import Backend.BrechoAPI.domain.enums.PaymentStatus;
import Backend.BrechoAPI.dto.response.OrderItemResponseDTO;
import Backend.BrechoAPI.dto.response.OrderResponseDTO;
import Backend.BrechoAPI.dto.response.PixResponseDTO;
import Backend.BrechoAPI.dto.response.ShippingResponseDTO;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.OrderItemRepository;
import Backend.BrechoAPI.repository.OrderRepository;
import Backend.BrechoAPI.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;



























@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final Clock clock;

    

























    public OrderResponseDTO findOwn(UUID userId, UUID orderId) {
        Objects.requireNonNull(userId, "userId é obrigatório");
        Objects.requireNonNull(orderId, "orderId é obrigatório");

        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ORDER_NOT_FOUND, "pedido não encontrado"));

        List<OrderItemResponseDTO> items = orderItemRepository
                .findAllByOrderIdOrderByIdAsc(orderId).stream()
                
                
                
                .map(item -> new OrderItemResponseDTO(item.getProduct().getId(),
                        item.getTitle(), item.getUnitPrice()))
                .toList();

        
        
        
        
        
        
        
        
        
        
        Payment payment = paymentRepository.findLiveByOrderId(orderId).orElse(null);
        boolean cobrancaPendente = payment != null
                && payment.getStatus() == PaymentStatus.PENDING_CONFIRMATION;

        return new OrderResponseDTO(order.getId(),
                statusEfetivoDe(order, cobrancaPendente, clock.instant()),
                order.getSubtotal(), order.getShippingCost(), order.total(), order.getExpiresAt(),
                items, enderecoDe(order.getShipping()),
                payment != null && payment.hasCharge()
                        ? new PixResponseDTO(payment.getTxid(), payment.getPixCopyPaste())
                        : null);
    }

    








































    private static String statusEfetivoDe(Order order, boolean cobrancaPendente, Instant now) {
        return order.isAwaitingPayment() && !cobrancaPendente && order.isDueAt(now)
                ? OrderStatus.EXPIRED.name()
                : order.getStatus().name();
    }

    




    private static ShippingResponseDTO enderecoDe(ShippingSnapshot shipping) {
        return new ShippingResponseDTO(shipping.getRecipientName(), shipping.getPhone(),
                shipping.getZipCode(), shipping.getStreet(), shipping.getNumber(),
                shipping.getComplement(), shipping.getDistrict(), shipping.getCity(),
                shipping.getUf().name());
    }
}
