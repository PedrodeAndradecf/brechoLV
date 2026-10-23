package Backend.BrechoAPI.domain;

import Backend.BrechoAPI.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;





















































@Getter
@Entity
@Table(name = "orders")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    
    
    
    
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    
    
    @Column(name = "shipping_cost", nullable = false, precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Embedded
    private ShippingSnapshot shipping;

    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Order(User user, ShippingSnapshot shipping, BigDecimal subtotal,
                  BigDecimal shippingCost, Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user é obrigatório");
        this.shipping = Objects.requireNonNull(shipping, "shipping é obrigatório");
        this.subtotal = requireNotNegative(subtotal, "subtotal");
        this.shippingCost = requireNotNegative(shippingCost, "shippingCost");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt é obrigatório");
        this.status = OrderStatus.AWAITING_PAYMENT;
    }

    








    public static Order create(User user, ShippingSnapshot shipping, BigDecimal subtotal,
                               BigDecimal shippingCost, Instant expiresAt) {
        return new Order(user, shipping, subtotal, shippingCost, expiresAt);
    }

    








    public void expire() {
        this.status = OrderStatus.EXPIRED;
    }

    



    public BigDecimal total() {
        return subtotal.add(shippingCost);
    }

    








    public boolean isDueAt(Instant now) {
        Objects.requireNonNull(now, "now é obrigatório");
        return !expiresAt.isAfter(now);
    }

    public boolean isAwaitingPayment() {
        return status == OrderStatus.AWAITING_PAYMENT;
    }

    private static BigDecimal requireNotNegative(BigDecimal value, String campo) {
        Objects.requireNonNull(value, campo + " é obrigatório");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(campo + " não pode ser negativo");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Order other = (Order) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
