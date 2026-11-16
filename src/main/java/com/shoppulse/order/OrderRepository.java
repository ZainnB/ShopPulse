package com.shoppulse.order;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Order queries that load lines and products up front so rendering never triggers N+1. */
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"items", "items.product"})
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select distinct o from Order o join fetch o.user join fetch o.items i join fetch i.product where o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") Long id);
}
