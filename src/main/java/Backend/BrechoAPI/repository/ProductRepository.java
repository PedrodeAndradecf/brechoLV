package Backend.BrechoAPI.repository;

import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.projection.ProductCardProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    
    
    
    
    @Query("""
           SELECT p.id AS id,
                  p.title AS title,
                  p.brand AS brand,
                  p.size AS size,
                  p.color AS color,
                  p.price AS price,
                  ph.storageKey AS coverStorageKey
           FROM Product p
           LEFT JOIN ProductPhoto ph ON ph.product = p AND ph.cover = true
           WHERE p.deletedAt IS NULL
             AND (:brand      IS NULL OR p.brand = :brand)
             AND (:color      IS NULL OR p.color = :color)
             AND (:size       IS NULL OR p.size = :size)
             AND (:categoryId IS NULL OR p.category.id = :categoryId)
             AND (:minPrice   IS NULL OR p.price >= :minPrice)
             AND (:maxPrice   IS NULL OR p.price <= :maxPrice)
           """)
    Page<ProductCardProjection> findForShowcase(@Param("brand") String brand,
                                                @Param("color") String color,
                                                @Param("size") String size,
                                                @Param("categoryId") UUID categoryId,
                                                @Param("minPrice") BigDecimal minPrice,
                                                @Param("maxPrice") BigDecimal maxPrice,
                                                Pageable pageable);

    
    
    
    @EntityGraph(attributePaths = {"category"})
    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);

    
    Page<Product> findBySellerIdAndDeletedAtIsNull(UUID sellerId, Pageable pageable);

    
    
    
    Page<Product> findBySellerId(UUID sellerId, Pageable pageable);

    Optional<Product> findByIdAndSellerIdAndDeletedAtIsNull(UUID id, UUID sellerId);

    
    @Query("SELECT DISTINCT p.brand FROM Product p WHERE p.deletedAt IS NULL ORDER BY p.brand")
    List<String> findDistinctBrands();

    @Query("SELECT DISTINCT p.color FROM Product p WHERE p.deletedAt IS NULL ORDER BY p.color")
    List<String> findDistinctColors();

    
    @Query("""
           SELECT DISTINCT p.size FROM Product p
           WHERE p.category.id = :categoryId
             AND p.deletedAt IS NULL
           ORDER BY p.size
           """)
    List<String> findDistinctSizesByCategory(@Param("categoryId") UUID categoryId);

    @Query("SELECT MIN(p.price) FROM Product p WHERE p.deletedAt IS NULL")
    BigDecimal findMinAvailablePrice();

    @Query("SELECT MAX(p.price) FROM Product p WHERE p.deletedAt IS NULL")
    BigDecimal findMaxAvailablePrice();
}
