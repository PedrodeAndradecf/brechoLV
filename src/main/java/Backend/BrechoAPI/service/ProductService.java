package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Product;
import Backend.BrechoAPI.dto.response.CategoryResponseDTO;
import Backend.BrechoAPI.dto.response.PagedResponseDTO;
import Backend.BrechoAPI.dto.response.ProductCardResponseDTO;
import Backend.BrechoAPI.dto.response.ProductDetailResponseDTO;
import Backend.BrechoAPI.dto.response.ProductFiltersResponseDTO;
import Backend.BrechoAPI.dto.response.ProductPhotoResponseDTO;
import Backend.BrechoAPI.exception.DomainRuleException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.projection.ProductCardProjection;
import Backend.BrechoAPI.repository.CategoryRepository;
import Backend.BrechoAPI.repository.ProductPhotoRepository;
import Backend.BrechoAPI.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final CategoryRepository categoryRepository;
    private final StorageService storageService;

    public PagedResponseDTO<ProductCardResponseDTO> listAvailable(String brand, String color, String size,
                                                                  UUID categoryId, BigDecimal minPrice,
                                                                  BigDecimal maxPrice, Pageable pageable) {

        validatePriceRange(minPrice, maxPrice);

        Page<ProductCardProjection> page = productRepository.findForShowcase(
                blankToNull(brand), blankToNull(color), blankToNull(size),
                categoryId, minPrice, maxPrice, pageable);

        return PagedResponseDTO.from(page.map(this::toCard));
    }

    




    public ProductDetailResponseDTO findDetail(UUID id) {
        Product product = productRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PRODUCT_NOT_FOUND, "peça não encontrada"));

        return toDetail(product);
    }

    




    public ProductFiltersResponseDTO availableFilters(UUID categoryId) {
        List<String> sizes = categoryId == null
                ? List.of()
                : productRepository.findDistinctSizesByCategory(categoryId);

        List<CategoryResponseDTO> categories = categoryRepository.findAllByOrderByNameAsc().stream()
                .map(c -> new CategoryResponseDTO(c.getId(), c.getName(), c.getSlug(), c.getSizeType()))
                .toList();

        return new ProductFiltersResponseDTO(
                productRepository.findDistinctBrands(),
                productRepository.findDistinctColors(),
                sizes,
                categories,
                productRepository.findMinAvailablePrice(),
                productRepository.findMaxAvailablePrice());
    }

    private void validatePriceRange(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new DomainRuleException(ErrorCode.DOMAIN_RULE_VIOLATION, "minPrice não pode ser maior que maxPrice");
        }
    }

    
    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private ProductCardResponseDTO toCard(ProductCardProjection p) {
        String coverUrl = p.getCoverStorageKey() == null
                ? null
                : storageService.buildPublicUrl(p.getCoverStorageKey());
        return new ProductCardResponseDTO(
                p.getId(), p.getTitle(), p.getBrand(), p.getSize(),
                p.getColor(), p.getPrice(), coverUrl);
    }

    ProductDetailResponseDTO toDetail(Product p) {
        List<ProductPhotoResponseDTO> photos = productPhotoRepository
                .findByProductIdOrderByPositionAsc(p.getId()).stream()
                .map(ph -> new ProductPhotoResponseDTO(
                        ph.getId(),
                        storageService.buildPublicUrl(ph.getStorageKey()),
                        ph.getPosition(),
                        ph.isCover()))
                .toList();

        return new ProductDetailResponseDTO(
                p.getId(), p.getTitle(), p.getDescription(), p.getBrand(), p.getSize(), p.getSizeLabel(),
                p.getColor(), p.getCondition(), p.getPrice(),
                new CategoryResponseDTO(p.getCategory().getId(), p.getCategory().getName(),
                        p.getCategory().getSlug(), p.getCategory().getSizeType()),
                photos);
    }
}
