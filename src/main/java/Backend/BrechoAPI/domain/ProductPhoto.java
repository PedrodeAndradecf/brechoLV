package Backend.BrechoAPI.domain;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;









@Getter
@Entity
@Table(name = "product_photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class ProductPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(nullable = false)
    private boolean cover;

    @Column(nullable = false)
    private Integer position;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private ProductPhoto(Product product, String storageKey, Integer position) {
        this.product = Objects.requireNonNull(product, "product é obrigatório");
        this.storageKey = Objects.requireNonNull(storageKey, "storageKey é obrigatória");
        this.position = Objects.requireNonNull(position, "position é obrigatória");
        this.cover = false;
    }

    public static ProductPhoto create(Product product, String storageKey, Integer position) {
        return new ProductPhoto(product, storageKey, position);
    }

    public void markAsCover() {
        this.cover = true;
    }

    public void unmarkCover() {
        this.cover = false;
    }

    public void reposition(Integer newPosition) {
        this.position = Objects.requireNonNull(newPosition, "position é obrigatória");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductPhoto other = (ProductPhoto) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
