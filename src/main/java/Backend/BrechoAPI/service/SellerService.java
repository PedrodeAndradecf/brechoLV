package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.domain.ProductPhoto;
import Backend.BrechoAPI.domain.User;
import Backend.BrechoAPI.dto.request.ProductCreateRequestDTO;
import Backend.BrechoAPI.dto.request.ProductUpdateRequestDTO;
import Backend.BrechoAPI.dto.response.PagedResponseDTO;
import Backend.BrechoAPI.dto.response.SellerProductCardResponseDTO;
import Backend.BrechoAPI.dto.response.ProductDetailResponseDTO;
import Backend.BrechoAPI.dto.response.ProductPhotoResponseDTO;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.ProductPhotoRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import Backend.BrechoAPI.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.util.List;
import java.util.UUID;






@Service
@RequiredArgsConstructor
public class SellerService {

    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;
    private final ProductService productService;
    private final ProductPhotoTransactionService photoTransactionService;
    private final StorageService storageService;
    private final Clock clock;

    








    @Transactional(readOnly = true)
    public PagedResponseDTO<SellerProductCardResponseDTO> listOwnProducts(UUID sellerId, Pageable pageable,
                                                                         boolean includeArchived) {
        Page<Product> page = includeArchived
                ? productRepository.findBySellerId(sellerId, pageable)
                : productRepository.findBySellerIdAndDeletedAtIsNull(sellerId, pageable);
        return PagedResponseDTO.from(page.map(this::toSellerCard));
    }

    @Transactional
    public ProductDetailResponseDTO createProduct(UUID sellerId, ProductCreateRequestDTO request) {
        User seller = userRepository.findByIdAndDeletedAtIsNull(sellerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SELLER_NOT_FOUND, "vendedora não encontrada"));
        Category category = categoryService.findEntityById(request.categoryId());
        requireSizeMatchesRegime(category, request.size());

        Product product = Product.create(seller, category, request.title(), request.description(),
                request.brand(), request.size(), request.sizeLabel(), request.color(),
                request.condition(), request.price());

        return productService.toDetail(productRepository.save(product));
    }

    @Transactional
    public ProductDetailResponseDTO updateProduct(UUID productId, ProductUpdateRequestDTO request) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "peça não encontrada"));
        Category category = categoryService.findEntityById(request.categoryId());
        requireSizeMatchesRegime(category, request.size());

        product.updateDetails(request.title(), request.description(), request.brand(),
                request.color(), request.condition());
        product.changePrice(request.price());
        product.reclassify(category, request.size(), request.sizeLabel());

        return productService.toDetail(product);
    }

    @Transactional
    public void archiveProduct(UUID productId) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "peça não encontrada"));
        product.archive(clock.instant());
    }

    
    public List<ProductPhotoResponseDTO> uploadPhotos(UUID productId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new DomainRuleException(ErrorCode.DOMAIN_RULE_VIOLATION, "nenhum arquivo enviado");
        }
        
        
        List<String> keys = files.stream()
                .map(file -> storageService.upload(file, "products/" + productId))
                .toList();

        
        List<ProductPhoto> saved = photoTransactionService.persist(productId, keys);

        return saved.stream().map(this::toPhotoResponse).toList();
    }

    public void deletePhoto(UUID productId, UUID photoId) {
        ProductPhoto photo = productPhotoRepository.findByIdAndProductId(photoId, productId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PHOTO_NOT_FOUND, "foto não encontrada"));
        String key = photo.getStorageKey();

        
        
        photoTransactionService.deleteRowAndPromoteCover(productId, photo.getId());
        storageService.delete(key);
    }

    public void setCoverPhoto(UUID productId, UUID photoId) {
        photoTransactionService.setCover(productId, photoId);
    }

    private void requireSizeMatchesRegime(Category category, String size) {
        if (!category.acceptsSize(size)) {
            throw new DomainRuleException(ErrorCode.DOMAIN_RULE_VIOLATION,
                    "tamanho '%s' não é válido para a categoria (regime %s)"
                            .formatted(size, category.getSizeType()));
        }
    }

    private SellerProductCardResponseDTO toSellerCard(Product p) {
        String coverUrl = productPhotoRepository.findByProductIdAndCoverIsTrue(p.getId())
                .map(ph -> storageService.buildPublicUrl(ph.getStorageKey()))
                .orElse(null);
        return new SellerProductCardResponseDTO(p.getId(), p.getTitle(), p.getBrand(),
                p.getSize(), p.getColor(), p.getPrice(), coverUrl, p.isArchived());
    }

    private ProductPhotoResponseDTO toPhotoResponse(ProductPhoto ph) {
        return new ProductPhotoResponseDTO(ph.getId(),
                storageService.buildPublicUrl(ph.getStorageKey()), ph.getPosition(), ph.isCover());
    }
}
