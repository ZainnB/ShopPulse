package com.shoppulse.analytics;

import com.shoppulse.common.CacheNames;
import com.shoppulse.order.OrderPlacedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Keeps the dashboard fresh after checkout: refreshes that day's rollup row and evicts
 * every dashboard cache.
 *
 * <p>AFTER_COMMIT matters for two reasons. The rollup query runs on its own connection,
 * so it can only see the new order once the checkout transaction has committed; running
 * earlier would aggregate stale data and then cache it. And if checkout rolls back (for
 * example on a stock conflict) the listener never fires, so a failed order can never
 * leave a phantom revenue figure or a needless cache flush behind.
 */
@Component
public class OrderPlacedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPlacedListener.class);

    private final DailySalesRollupService rollup;
    private final CacheManager cacheManager;

    public OrderPlacedListener(DailySalesRollupService rollup, CacheManager cacheManager) {
        this.rollup = rollup;
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(OrderPlacedEvent event) {
        try {
            LocalDate day = event.placedAt().atZone(ZoneId.systemDefault()).toLocalDate();
            rollup.rebuildDay(day);
        } catch (RuntimeException e) {
            // The order itself is safe; the nightly job will heal the rollup row.
            log.warn("Could not refresh daily_sales for order {}", event.orderId(), e);
        }
        evictDashboardCaches();
    }

    /** Heals any rollup the event path missed (crash between commit and listener, etc.). */
    @Scheduled(cron = "0 15 2 * * *")
    public void nightlyRebuild() {
        LocalDate today = LocalDate.now();
        rollup.rebuildRange(today.minusDays(2), today);
        evictDashboardCaches();
        log.info("Nightly daily_sales rebuild completed for {} to {}", today.minusDays(2), today);
    }

    private void evictDashboardCaches() {
        for (String name : CacheNames.ALL) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }
}
