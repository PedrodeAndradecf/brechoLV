package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.ShippingSnapshot;
import Backend.BrechoAPI.dto.response.OrderItemResponseDTO;
import Backend.BrechoAPI.dto.response.OrderResponseDTO;
import Backend.BrechoAPI.dto.response.PixResponseDTO;
import Backend.BrechoAPI.dto.response.ShippingResponseDTO;
import Backend.BrechoAPI.exception.PaymentGatewayException;
import Backend.BrechoAPI.service.CheckoutTransactionService.CheckoutOutcome;
import Backend.BrechoAPI.service.CheckoutTransactionService.LiveAttemptExistsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

































































@Service
@RequiredArgsConstructor
public class CheckoutService {

    private final CheckoutTransactionService transactionService;
    private final PaymentGateway paymentGateway;
    private final Clock clock;

    
















    public CheckoutResult checkout(UUID userId, UUID addressId) {
        
        
        
        
        
        
        CheckoutOutcome outcome = transactionService.createOrder(userId, addressId, clock.instant());

        if (!outcome.hasCharge()) {
            
            
            
            
            
            
            
            
            UUID attemptId;
            try {
                attemptId = transactionService.openAttempt(outcome.orderId());
            } catch (LiveAttemptExistsException jaHaTentativaViva) {
                return new CheckoutResult(respostaDe(outcome), outcome.resumed());
            }

            
            
            
            
            
            
            
            
            
            PaymentGateway.PixCharge charge = cobrar(outcome, attemptId);

            
            
            
            
            
            
            transactionService.attachCharge(attemptId, charge);

            
            
            
            
            outcome = outcome.withCharge(charge.txid(), charge.copyPaste());
        }

        return new CheckoutResult(respostaDe(outcome), outcome.resumed());
    }

    































    private PaymentGateway.PixCharge cobrar(CheckoutOutcome outcome, UUID attemptId) {
        try {
            return paymentGateway.createPixCharge(outcome.orderId(), outcome.total(),
                    outcome.expiresAt());
        } catch (PaymentGatewayException e) {
            if (!e.isOutcomeUnknown()) {
                
                
                
                transactionService.markAttemptNotCreated(attemptId);
            }
            throw e;
        } catch (RuntimeException e) {
            
            throw PaymentGatewayException.outcomeUnknown(e);
        }
    }

    

    










    public record CheckoutResult(OrderResponseDTO order, boolean resumed) {}

    





















    private static OrderResponseDTO respostaDe(CheckoutOutcome outcome) {
        List<OrderItemResponseDTO> items = outcome.items().stream()
                
                
                .map(item -> new OrderItemResponseDTO(item.productId(), item.title(),
                        item.unitPrice()))
                .toList();

        return new OrderResponseDTO(outcome.orderId(), outcome.status().name(), outcome.subtotal(),
                outcome.shippingCost(), outcome.total(), outcome.expiresAt(), items,
                enderecoDe(outcome.shipping()),
                outcome.hasCharge()
                        ? new PixResponseDTO(outcome.txid(), outcome.pixCopyPaste())
                        : null);
    }

    




    private static ShippingResponseDTO enderecoDe(ShippingSnapshot shipping) {
        return new ShippingResponseDTO(shipping.getRecipientName(), shipping.getPhone(),
                shipping.getZipCode(), shipping.getStreet(), shipping.getNumber(),
                shipping.getComplement(), shipping.getDistrict(), shipping.getCity(),
                shipping.getUf().name());
    }
}
