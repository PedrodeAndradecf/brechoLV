package Backend.BrechoAPI.service;

import Backend.BrechoAPI.config.CheckoutProperties;
import Backend.BrechoAPI.domain.Address;
import Backend.BrechoAPI.domain.Cart;
import Backend.BrechoAPI.domain.CartItem;
import Backend.BrechoAPI.domain.Order;
import Backend.BrechoAPI.domain.OrderItem;
import Backend.BrechoAPI.domain.Payment;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.ShippingSnapshot;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.domain.enums.OrderStatus;
import Backend.BrechoAPI.exception.ConflictException;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.PaymentGatewayException;
import Backend.BrechoAPI.exception.PersistenceIntegrityException;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.AddressRepository;
import Backend.BrechoAPI.repository.CartItemRepository;
import Backend.BrechoAPI.repository.CartRepository;
import Backend.BrechoAPI.repository.OrderItemRepository;
import Backend.BrechoAPI.repository.OrderRepository;
import Backend.BrechoAPI.repository.PaymentRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;















































@Service
@RequiredArgsConstructor
public class CheckoutTransactionService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutTransactionService.class);

    





    static final String INDICE_DE_PEDIDO_ABERTO = "uk_orders_awaiting_per_user";

    



    private static final String UNIQUE_VIOLATION = "23505";

    





    static final String INDICE_DA_TENTATIVA_VIVA = "\"uk_payments_order\"";

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ShippingService shippingService;
    private final OrderExpiryService orderExpiryService;
    private final CheckoutProperties checkoutProperties;
    private final Clock clock;

    














































    @Transactional
    public CheckoutOutcome createOrder(UUID userId, UUID addressId, Instant now) {
        Objects.requireNonNull(userId, "userId é obrigatório");
        Objects.requireNonNull(addressId, "addressId é obrigatório");
        Objects.requireNonNull(now, "now é obrigatório");

        
        
        
        
        
        User user = userRepository.findById(userId).orElseThrow(() -> {
            log.error("usuário autenticado não encontrado: {}", userId);
            return new PersistenceIntegrityException();
        });

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new DomainRuleException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "conta sem telefone não pode fechar pedido: o telefone é congelado no pedido"
                            + " para o despacho");
        }

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ADDRESS_NOT_FOUND, "endereço não encontrado"));

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        orderExpiryService.expireDueOf(userId, now);

        Order aberto = orderRepository.findByUserIdAndStatus(userId, OrderStatus.AWAITING_PAYMENT)
                .orElse(null);

        
        
        
        List<CartItem> itensDoCarrinho = itensDoCarrinhoDe(userId);

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        if (aberto != null) {
            List<OrderItem> itensDoPedidoAberto = itensDoPedido(aberto.getId());
            Set<UUID> doPedido = idsDasPecas(itensDoPedidoAberto);
            Set<UUID> doCarrinho = itensDoCarrinho.stream()
                    .map(item -> item.getProduct().getId())
                    .collect(Collectors.toSet());

            
            
            
            
            
            
            
            
            boolean corresponde = doPedido.equals(doCarrinho)
                    && aberto.getShipping().equals(ShippingSnapshot.copyOf(address, user));

            if (!corresponde) {
                throw new ConflictException(ErrorCode.ORDER_ALREADY_OPEN,
                        "você já tem um pedido aguardando pagamento (" + aberto.getId()
                                + "), com entrega em " + enderecoCongeladoDe(aberto)
                                + " e prazo até " + aberto.getExpiresAt()
                                + "; conclua ou aguarde o prazo antes de fechar outro");
            }

            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            conferirDisponibilidadeDoPedidoAberto(aberto, itensDoPedidoAberto);

            return outcomeDe(aberto, true);
        }

        
        
        
        
        
        
        BigDecimal shippingCost = shippingService.costFor(address.getUf());

        
        
        
        
        
        
        
        
        
        
        if (itensDoCarrinho.isEmpty()) {
            throw new DomainRuleException(ErrorCode.CART_EMPTY,
                    "seu carrinho está vazio: não há o que fechar");
        }

        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        List<Product> disponiveis = conferirDisponibilidade(itensDoCarrinho);

        
        
        
        
        
        
        
        ShippingSnapshot shipping = ShippingSnapshot.copyOf(address, user);

        
        
        
        BigDecimal subtotal = disponiveis.stream()
                .map(Product::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.create(user, shipping, subtotal, shippingCost,
                now.plus(checkoutProperties.orderTtl()));

        
        
        
        
        
        
        
        
        
        try {
            orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            
            
            
            
            
            
            
            
            
            
            
            
            
            if (UNIQUE_VIOLATION.equals(sqlStateDe(e)) && mensagemNomeia(e, INDICE_DE_PEDIDO_ABERTO)) {
                
                
                
                throw new ConflictException(ErrorCode.ORDER_ALREADY_OPEN,
                        "você já tem um pedido aguardando pagamento; conclua ou aguarde o prazo"
                                + " antes de fechar outro");
            }
            
            
            
            
            
            
            
            
            log.error("violação de integridade inesperada ao criar pedido do usuário {}", userId, e);
            throw new PersistenceIntegrityException();
        }

        
        
        orderItemRepository.saveAll(disponiveis.stream()
                .map(produto -> OrderItem.create(order, produto))
                .toList());

        
        
        
        
        
        
        
        
        
        
        

        
        
        
        
        
        
        
        
        return montar(order, itensDoPedido(order.getId()), null, null, false);
    }

    



























    @Transactional
    public UUID openAttempt(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId é obrigatório");

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.ORDER_NOT_FOUND, "pedido não encontrado"));
        Payment attempt = Payment.openAttempt(order);

        
        
        try {
            paymentRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException e) {
            if (UNIQUE_VIOLATION.equals(sqlStateDe(e)) && mensagemNomeia(e, INDICE_DA_TENTATIVA_VIVA)) {
                throw new LiveAttemptExistsException(orderId);
            }
            log.error("violação de integridade inesperada na K1 do pedido {}", orderId, e);
            throw new PersistenceIntegrityException();
        }
        return attempt.getId();
    }

    
































    @Transactional
    public void attachCharge(UUID paymentId, PaymentGateway.PixCharge charge) {
        Objects.requireNonNull(paymentId, "paymentId é obrigatório");
        Objects.requireNonNull(charge, "charge é obrigatória");
        Objects.requireNonNull(charge.copyPaste(), "copyPaste é obrigatório");

        int linhas = paymentRepository.openWithCharge(paymentId, charge.txid(), charge.copyPaste(),
                clock.instant());
        if (linhas == 1) {
            return;
        }

        
        
        
        
        String orderNsu = paymentRepository.findById(paymentId)
                .map(Payment::getOrderNsu)
                .orElse("(tentativa " + paymentId + " não encontrada)");
        log.error("K2 perdeu: a tentativa {} já não estava PENDING_CONFIRMATION quando a porta"
                        + " devolveu (decisão humana antes da volta da chamada). Artefato NÃO gravado,"
                        + " única cópia: order_nsu={} txid={} artefato={}",
                paymentId, orderNsu, charge.txid(), charge.copyPaste());
        throw PaymentGatewayException.outcomeUnknown(null);
    }

    













    @Transactional
    public void markAttemptNotCreated(UUID paymentId) {
        Objects.requireNonNull(paymentId, "paymentId é obrigatório");
        paymentRepository.markNotCreated(paymentId, clock.instant());
    }

    





    public static final class LiveAttemptExistsException extends RuntimeException {
        public LiveAttemptExistsException(UUID orderId) {
            super("o pedido " + orderId + " já tem tentativa de cobrança viva");
        }
    }

    

    


    private List<CartItem> itensDoCarrinhoDe(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        return cart == null ? List.of() : cartItemRepository.findAllByCartId(cart.getId());
    }

    















    private List<OrderItem> itensDoPedido(UUID orderId) {
        return orderItemRepository.findAllByOrderIdOrderByIdAsc(orderId);
    }

    private static Set<UUID> idsDasPecas(List<OrderItem> itens) {
        return itens.stream().map(item -> item.getProduct().getId()).collect(Collectors.toSet());
    }

    

















    private List<Product> conferirDisponibilidade(List<CartItem> itensDoCarrinho) {
        return itensDoCarrinho.stream()
                .map(item -> exigirDisponivel(item.getProduct().getId(),
                        
                        
                        
                        () -> "a peça \"" + item.getProduct().getTitle() + "\" não está mais"
                                + " disponível; remova-a do carrinho para continuar"))
                .toList();
    }

    























    private void conferirDisponibilidadeDoPedidoAberto(Order aberto, List<OrderItem> itens) {
        for (OrderItem item : itens) {
            
            
            
            exigirDisponivel(item.getProduct().getId(),
                    () -> "a peça \"" + item.getTitle() + "\" não está mais disponível: o pedido "
                            + aberto.getId() + " não pode mais ser pago, e ele se encerra sozinho"
                            + " no fim do prazo, em " + aberto.getExpiresAt());
        }
    }

    




    private Product exigirDisponivel(UUID productId, Supplier<String> recusa) {
        return productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ConflictException(
                        ErrorCode.PRODUCT_NOT_AVAILABLE, recusa.get()));
    }

    


    private static String enderecoCongeladoDe(Order order) {
        ShippingSnapshot congelado = order.getShipping();
        return congelado.getStreet() + ", " + congelado.getNumber()
                + " — " + congelado.getCity() + "/" + congelado.getUf();
    }

    

    














    private static String sqlStateDe(Throwable erro) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            if (atual instanceof SQLException sql) {
                return sql.getSQLState();
            }
            if (atual.getCause() == atual) {
                break;  
            }
        }
        return null;
    }

    

    private static boolean mensagemNomeia(Throwable erro, String nome) {
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            String mensagem = atual.getMessage();
            if (mensagem != null && mensagem.contains(nome)) {
                return true;
            }
            if (atual.getCause() == atual) {
                break;
            }
        }
        return false;
    }

    

    































    public record CheckoutOutcome(UUID orderId, OrderStatus status, BigDecimal subtotal,
                                  BigDecimal shippingCost, BigDecimal total, Instant expiresAt,
                                  ShippingSnapshot shipping, List<Item> items,
                                  String txid, String pixCopyPaste, boolean resumed) {

        

        public record Item(UUID productId, String title, BigDecimal unitPrice) {}

        









        public boolean hasCharge() {
            return pixCopyPaste != null;
        }

        











        public CheckoutOutcome withCharge(String txid, String pixCopyPaste) {
            return new CheckoutOutcome(orderId, status, subtotal, shippingCost, total, expiresAt,
                    shipping, items, txid,
                    Objects.requireNonNull(pixCopyPaste, "copyPaste é obrigatório"), resumed);
        }
    }

    





    private CheckoutOutcome outcomeDe(Order order, boolean resumed) {
        Payment payment = paymentRepository.findLiveByOrderId(order.getId()).orElse(null);
        return montar(order, itensDoPedido(order.getId()),
                payment == null ? null : payment.getTxid(),
                payment == null ? null : payment.getPixCopyPaste(),
                resumed);
    }

    

    private static CheckoutOutcome montar(Order order, List<OrderItem> itens, String txid,
                                          String pixCopyPaste, boolean resumed) {
        List<CheckoutOutcome.Item> views = itens.stream()
                
                
                .map(item -> new CheckoutOutcome.Item(item.getProduct().getId(),
                        item.getTitle(), item.getUnitPrice()))
                .toList();

        return new CheckoutOutcome(order.getId(), order.getStatus(), order.getSubtotal(),
                order.getShippingCost(), order.total(), order.getExpiresAt(), order.getShipping(),
                views, txid, pixCopyPaste, resumed);
    }
}
