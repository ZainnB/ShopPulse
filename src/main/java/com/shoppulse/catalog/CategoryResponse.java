package com.shoppulse.catalog;

/** API shape of a category, so the entity never leaves the service layer. */
public record CategoryResponse(Long id, String name, String slug) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug());
    }
}
