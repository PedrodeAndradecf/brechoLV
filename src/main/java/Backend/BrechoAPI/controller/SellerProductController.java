package Backend.BrechoAPI.controller;

import Backend.BrechoAPI.dto.request.ProductCreateRequestDTO;
import Backend.BrechoAPI.dto.request.ProductUpdateRequestDTO;
import Backend.BrechoAPI.dto.response.PagedResponseDTO;
import Backend.BrechoAPI.dto.response.SellerProductCardResponseDTO;
import Backend.BrechoAPI.dto.response.ProductDetailResponseDTO;
import Backend.BrechoAPI.dto.response.ProductPhotoResponseDTO;
import Backend.BrechoAPI.security.CustomUserDetails;
import Backend.BrechoAPI.service.SellerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/seller/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerProductController {

    private final SellerService sellerService;

    
    @GetMapping
    public ResponseEntity<PagedResponseDTO<SellerProductCardResponseDTO>> listOwn(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {

        return ResponseEntity.ok(sellerService.listOwnProducts(user.getId(), pageable, includeArchived));
    }

    @PostMapping
    public ResponseEntity<ProductDetailResponseDTO> create(@AuthenticationPrincipal CustomUserDetails user,
                                                           @Valid @RequestBody ProductCreateRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sellerService.createProduct(user.getId(), request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@productSecurity.isOwner(#id, authentication)")
    public ResponseEntity<ProductDetailResponseDTO> update(@PathVariable UUID id,
                                                           @Valid @RequestBody ProductUpdateRequestDTO request) {
        return ResponseEntity.ok(sellerService.updateProduct(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@productSecurity.isOwner(#id, authentication)")
    public ResponseEntity<Void> archive(@PathVariable UUID id) {
        sellerService.archiveProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@productSecurity.isOwner(#id, authentication)")
    public ResponseEntity<List<ProductPhotoResponseDTO>> uploadPhotos(@PathVariable UUID id,
                                                                      @RequestParam("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sellerService.uploadPhotos(id, files));
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    @PreAuthorize("@productSecurity.isOwner(#id, authentication)")
    public ResponseEntity<Void> deletePhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        sellerService.deletePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/photos/{photoId}/cover")
    @PreAuthorize("@productSecurity.isOwner(#id, authentication)")
    public ResponseEntity<Void> setCover(@PathVariable UUID id, @PathVariable UUID photoId) {
        sellerService.setCoverPhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }
}
