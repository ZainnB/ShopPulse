package com.shoppulse.order;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published after an order is committed. Listeners (analytics rollup, cache eviction)
 * run on @TransactionalEventListener(AFTER_COMMIT) so they never see uncommitted data.
 */
public record OrderPlacedEvent(Long orderId, Long userId, BigDecimal total, Instant placedAt) {
}
