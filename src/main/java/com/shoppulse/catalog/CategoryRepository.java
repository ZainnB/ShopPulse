package com.shoppulse.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for categories, kept separate so the service can order them in SQL. */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAsc();
}
