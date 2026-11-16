package com.shoppulse.catalog;

import com.shoppulse.auth.CurrentUserService;
import com.shoppulse.auth.Role;
import com.shoppulse.common.NotFoundException;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog business rules: customers only ever see active products, merchants may opt in
 * to inactive ones. Write access is enforced at the controller; the visibility rule lives
 * here so no caller can bypass it.
 */
@Service
public class ProductService {

    static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final CurrentUserService currentUserService;

    public ProductService(ProductRepository productRepository,
                          CategoryRepository categoryRepository,
                          CurrentUserService currentUserService) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String query, Long categoryId, boolean includeInactive, Pageable pageable) {
        boolean showInactive = includeInactive && isMerchant();
        Pageable bounded = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE),
                pageable.getSort().isSorted() ? pageable.getSort() : Sort.by("name"));
        return productRepository.findAll(filter(query, categoryId, showInactive), bounded)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id, boolean includeInactive) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id));
        if (!product.isActive() && !(includeInactive && isMerchant())) {
            throw NotFoundException.of("Product", id);
        }
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        Category category = findCategory(request.categoryId());
        Product product = new Product(request.name().trim(), descriptionOf(request),
                request.price(), request.stock(), category);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findWithCategoryById(id)
                .orElseThrow(() -> NotFoundException.of("Product", id));
        product.setName(request.name().trim());
        product.setDescription(descriptionOf(request));
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(findCategory(request.categoryId()));
        return ProductResponse.from(product);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> NotFoundException.of("Category", categoryId));
    }

    private static String descriptionOf(ProductRequest request) {
        return request.description() == null ? "" : request.description();
    }

    private boolean isMerchant() {
        return currentUserService.currentUser()
                .map(u -> u.getRole() == Role.MERCHANT)
                .orElse(false);
    }

    private static Specification<Product> filter(String query, Long categoryId, boolean includeInactive) {
        return (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!includeInactive) {
                predicates.add(cb.isTrue(root.get("active")));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (query != null && !query.isBlank()) {
                String escaped = query.trim().toLowerCase()
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + escaped + "%", '\\'));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
