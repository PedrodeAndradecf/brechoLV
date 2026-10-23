package Backend.BrechoAPI.domain;


import Backend.BrechoAPI.domain.enums.ProductCondition;
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
@Table(name = "products")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 80)
    private String brand;

    
    
    @Column(nullable = false, length = 10)
    private String size;

    
    @Column(name = "size_label", length = 30)
    private String sizeLabel;

    @Column(nullable = false, length = 40)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_condition", nullable = false, length = 20)
    private ProductCondition condition;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    
    
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private Long version;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Product(User seller, Category category, String title, String description, String brand,
                    String size, String sizeLabel, String color, ProductCondition condition, BigDecimal price) {
        this.seller = Objects.requireNonNull(seller, "seller é obrigatório");
        this.category = Objects.requireNonNull(category, "category é obrigatória");
        this.title = Objects.requireNonNull(title, "title é obrigatório");
        this.description = Objects.requireNonNull(description, "description é obrigatória");
        this.brand = Objects.requireNonNull(brand, "brand é obrigatória");
        this.size = Objects.requireNonNull(size, "size é obrigatório");
        this.sizeLabel = sizeLabel;
        this.color = Objects.requireNonNull(color, "color é obrigatória");
        this.condition = Objects.requireNonNull(condition, "condition é obrigatória");
        this.price = requirePositive(price);
    }

    public static Product create(User seller, Category category, String title, String description,
                                 String brand, String size, String sizeLabel, String color,
                                 ProductCondition condition, BigDecimal price) {
        return new Product(seller, category, title, description, brand, size, sizeLabel, color, condition, price);
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        Objects.requireNonNull(value, "price é obrigatório");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("price deve ser maior que zero");
        }
        return value;
    }

    








    public void updateDetails(String title, String description, String brand, String color,
                              ProductCondition condition) {
        this.title = Objects.requireNonNull(title, "title é obrigatório");
        this.description = Objects.requireNonNull(description, "description é obrigatória");
        this.brand = Objects.requireNonNull(brand, "brand é obrigatória");
        this.color = Objects.requireNonNull(color, "color é obrigatória");
        this.condition = Objects.requireNonNull(condition, "condition é obrigatória");
    }

    public void changePrice(BigDecimal newPrice) {
        this.price = requirePositive(newPrice);
    }

    

    public void reclassify(Category newCategory, String newSize, String newSizeLabel) {
        this.category = Objects.requireNonNull(newCategory, "category é obrigatória");
        this.size = Objects.requireNonNull(newSize, "size é obrigatório");
        this.sizeLabel = newSizeLabel;
    }

    
    public void archive(Instant now) {
        this.deletedAt = Objects.requireNonNull(now, "now é obrigatório");
    }

    public boolean isAvailable() {
        return deletedAt == null;
    }

    public boolean isArchived() {
        return deletedAt != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product other = (Product) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
