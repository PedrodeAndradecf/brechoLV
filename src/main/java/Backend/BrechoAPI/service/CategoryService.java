package Backend.BrechoAPI.service;

import Backend.BrechoAPI.domain.Category;
import Backend.BrechoAPI.dto.request.CategoryRequestDTO;
import Backend.BrechoAPI.dto.response.CategoryResponseDTO;
import Backend.BrechoAPI.exception.DuplicateResourceException;
import Backend.BrechoAPI.exception.ErrorCode;
import Backend.BrechoAPI.exception.ResourceNotFoundException;
import Backend.BrechoAPI.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public List<CategoryResponseDTO> list() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    

    public Category findEntityById(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND, "categoria não encontrada"));
    }

    @Transactional
    public CategoryResponseDTO create(CategoryRequestDTO request) {
        if (categoryRepository.existsBySlug(request.slug().trim().toLowerCase())) {
            throw new DuplicateResourceException(ErrorCode.CATEGORY_SLUG_ALREADY_EXISTS,
                    "já existe categoria com esse slug");
        }
        Category category = Category.create(request.name(), request.slug(), request.sizeType());
        return toResponse(categoryRepository.save(category));
    }

    private CategoryResponseDTO toResponse(Category c) {
        return new CategoryResponseDTO(c.getId(), c.getName(), c.getSlug(), c.getSizeType());
    }
}
