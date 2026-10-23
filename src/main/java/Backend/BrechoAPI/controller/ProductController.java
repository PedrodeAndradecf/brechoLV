package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.dto.response.*;
import Backend.BrechoAPI.service.ProductService;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Validated
public class ProductController {

    private final ProductService productService;

    








    @GetMapping
    public ResponseEntity<PagedResponseDTO<ProductCardResponseDTO>> list(
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) String color,
            @RequestParam(name = "productSize", required = false) String productSize,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) @PositiveOrZero BigDecimal minPrice,
            @RequestParam(required = false) @PositiveOrZero BigDecimal maxPrice,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(
                productService.listAvailable(brand, color, productSize, categoryId, minPrice, maxPrice, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductDetailResponseDTO> detail(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findDetail(id));
    }

    @GetMapping("/filters")
    public ResponseEntity<ProductFiltersResponseDTO> filters(
            @RequestParam(required = false) UUID categoryId) {
        
        return ResponseEntity.ok(productService.availableFilters(categoryId));
    }
}
