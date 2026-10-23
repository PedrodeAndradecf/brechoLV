package Backend.BrechoAPI.repository;

import Backend.BrechoAPI.domain.ProductPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, UUID> {

    List<ProductPhoto> findByProductIdOrderByPositionAsc(UUID productId);

    Optional<ProductPhoto> findByIdAndProductId(UUID id, UUID productId);

    
    Optional<ProductPhoto> findByProductIdAndCoverIsTrue(UUID productId);

    @Query("SELECT COALESCE(MAX(ph.position), 0) FROM ProductPhoto ph WHERE ph.product.id = :productId")
    Integer findMaxPosition(@Param("productId") UUID productId);
}