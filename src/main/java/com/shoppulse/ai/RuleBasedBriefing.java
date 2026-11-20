package com.shoppulse.ai;

import java.math.BigDecimal;
import java.util.Locale;

/** Deterministic five-bullet briefing used when no model is available. */
final class RuleBasedBriefing {

    private RuleBasedBriefing() {
    }

    static String write(WeekStats s) {
        StringBuilder sb = new StringBuilder();

        BigDecimal change = s.revenueChangePercent();
        String revenueLine = "Revenue was " + money(s.revenue()) + (change == null
                ? " (no prior week to compare)."
                : " (" + signed(change) + "% vs last week's " + money(s.previousRevenue()) + ").");
        String revenueAction = change != null && change.signum() < 0
                ? "Suggested action: run a short promotion on your best sellers to win back momentum."
                : "Suggested action: keep stock of the leading products healthy so the momentum continues.";
        bullet(sb, revenueLine, revenueAction);

        BigDecimal aov = s.averageOrderValue();
        BigDecimal prevAov = s.previousAverageOrderValue();
        String ordersLine = s.orders() + " orders at an average of " + money(aov)
                + (s.previousOrders() == 0 ? "." : " (last week: " + s.previousOrders() + " orders, " + money(prevAov) + ").");
        String ordersAction = aov.compareTo(prevAov) < 0
                ? "Suggested action: bundle complementary products to lift basket size."
                : "Suggested action: offer free shipping above a threshold slightly over " + money(aov) + " to nudge baskets up.";
        bullet(sb, ordersLine, ordersAction);

        WeekStats.CategoryChange best = s.bestCategory();
        if (best != null) {
            bullet(sb, "Best category: " + best.name() + " at " + money(best.revenue()) + " (" + signed(best.changePercent()) + "% week on week).",
                    "Suggested action: feature " + best.name() + " on the storefront front page.");
        } else {
            bullet(sb, "No category had a prior week to compare against.", "Suggested action: check back next week for category trends.");
        }

        WeekStats.CategoryChange worst = s.worstCategory();
        if (worst != null && worst != best) {
            bullet(sb, "Weakest category: " + worst.name() + " at " + money(worst.revenue()) + " (" + signed(worst.changePercent()) + "% week on week).",
                    "Suggested action: review pricing and visibility for " + worst.name() + ".");
        } else {
            bullet(sb, "Only one category could be compared week on week.", "Suggested action: widen the catalogue to diversify revenue.");
        }

        String products = s.topProducts().isEmpty() ? "no products sold" : String.join(", ",
                s.topProducts().stream().map(p -> p.name() + " (" + money(p.revenue()) + ")").toList());
        String repeat = s.returningBuyerSharePercent() == null ? ""
                : " Of the customers who bought this week, " + s.returningBuyerSharePercent()
                  + "% have ordered more than once before.";
        bullet(sb, "Top products: " + products + "." + repeat,
                "Suggested action: send a thank-you offer to repeat buyers and make sure the top products stay in stock.");
        return sb.toString().trim();
    }

    private static void bullet(StringBuilder sb, String insight, String action) {
        sb.append("- ").append(insight).append(' ').append(action).append('\n');
    }

    static String money(BigDecimal v) {
        return String.format(Locale.US, "$%,.2f", v);
    }

    static String signed(BigDecimal v) {
        return (v.signum() >= 0 ? "+" : "") + v.toPlainString();
    }
}
