package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.ProductPhoto;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.ProductPhotoRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;







@Service
@RequiredArgsConstructor
public class ProductPhotoTransactionService {

    private final ProductRepository productRepository;
    private final ProductPhotoRepository photoRepository;

    

    @Transactional
    public List<ProductPhoto> persist(UUID productId, List<String> storageKeys) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "peça não encontrada"));

        int position = photoRepository.findMaxPosition(productId);
        boolean hasCover = photoRepository.findByProductIdAndCoverIsTrue(productId).isPresent();

        List<ProductPhoto> saved = new ArrayList<>();
        for (String key : storageKeys) {
            ProductPhoto photo = ProductPhoto.create(product, key, ++position);
            if (!hasCover) {
                photo.markAsCover();
                hasCover = true;
            }
            saved.add(photoRepository.save(photo));
        }
        return saved;
    }

    




















    @Transactional
    public void deleteRowAndPromoteCover(UUID productId, UUID photoId) {
        ProductPhoto photo = photoRepository.findByIdAndProductId(photoId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PHOTO_NOT_FOUND, "foto não encontrada"));
        boolean eraCapa = photo.isCover();

        photoRepository.delete(photo);

        if (eraCapa) {
            photoRepository.flush();
            photoRepository.findByProductIdOrderByPositionAsc(productId).stream()
                    .findFirst()
                    .ifPresent(ProductPhoto::markAsCover);
        }
    }

    

    @Transactional
    public void setCover(UUID productId, UUID photoId) {
        ProductPhoto target = photoRepository.findByIdAndProductId(photoId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PHOTO_NOT_FOUND, "foto não encontrada"));

        photoRepository.findByProductIdAndCoverIsTrue(productId).ifPresent(current -> {
            if (!current.getId().equals(photoId)) {
                current.unmarkCover();
                photoRepository.flush();
            }
        });

        target.markAsCover();
    }
}
