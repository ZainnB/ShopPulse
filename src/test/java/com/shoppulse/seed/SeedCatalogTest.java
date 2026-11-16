package com.shoppulse.seed;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SeedCatalogTest {

    @Test
    void hasEightCategoriesAndPlausibleProductCount() {
        List<SeedCatalog.CategorySeed> cats = SeedCatalog.categories();
        assertEquals(8, cats.size());
        int products = cats.stream().mapToInt(c -> c.products().size()).sum();
        assertTrue(products >= 60 && products <= 80, "product count " + products);
    }

    @Test
    void slugsAndNamesAreUniqueAndPricesPositive() {
        Set<String> slugs = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (SeedCatalog.CategorySeed c : SeedCatalog.categories()) {
            assertTrue(slugs.add(c.slug()), "duplicate slug " + c.slug());
            for (SeedCatalog.ProductSeed p : c.products()) {
                assertTrue(names.add(p.name()), "duplicate product " + p.name());
                assertTrue(p.price().signum() > 0);
            }
        }
        assertEquals("home-kitchen", SeedCatalog.categories().get(1).slug());
    }
}
