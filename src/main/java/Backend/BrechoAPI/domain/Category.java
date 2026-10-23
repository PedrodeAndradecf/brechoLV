package Backend.BrechoAPI.domain;


import Backend.BrechoAPI.domain.enums.CategorySizeType;
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
@Table(name = "categories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String slug;

    
    
    @Enumerated(EnumType.STRING)
    @Column(name = "size_type", nullable = false, length = 10)
    private CategorySizeType sizeType;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    private Category(String name, String slug, CategorySizeType sizeType) {
        this.name = Objects.requireNonNull(name, "name é obrigatório");
        this.slug = Objects.requireNonNull(slug, "slug é obrigatório").trim().toLowerCase();
        this.sizeType = Objects.requireNonNull(sizeType, "sizeType é obrigatório");
    }

    public static Category create(String name, String slug, CategorySizeType sizeType) {
        return new Category(name, slug, sizeType);
    }

    

    public boolean acceptsSize(String size) {
        return sizeType.allows(size);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Category other = (Category) o;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
