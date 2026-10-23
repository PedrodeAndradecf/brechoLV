package Backend.BrechoAPI.domain;

import Backend.BrechoAPI.domain.enums.PaymentStatus;
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
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    




    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, updatable = false)
    private Order order;

    


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentStatus status;

    



    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    


    @Column(name = "order_nsu", nullable = false, length = 64, updatable = false)
    private String orderNsu;

    


    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_outcome", length = 30)
    private PaymentStatus reviewOutcome;

    
    
    
    
    
    
    
    
    @Column(length = 80)
    private String txid;

    @Column(name = "pix_copy_paste", columnDefinition = "TEXT")
    private String pixCopyPaste;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Payment(Order order) {
        this.order = Objects.requireNonNull(order, "order é obrigatório");
        this.status = PaymentStatus.PENDING_CONFIRMATION;
        this.amount = order.total();
        this.orderNsu = Objects.requireNonNull(order.getId(), "o pedido já tem de ter id").toString();
    }

    















    public static Payment openAttempt(Order order) {
        return new Payment(order);
    }

    












    public boolean hasCharge() {
        return pixCopyPaste != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Payment other = (Payment) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
