package RiskShield.analysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpendingProfile(
        UUID userId,
        Instant from,
        Instant to,
        int lookbackDays,
        boolean sufficientHistory,

        int purchaseCount,
        BigDecimal totalSpend,
        BigDecimal averagePurchase,
        BigDecimal medianPurchase,
        BigDecimal averageMonthlySpend,

        double averagePurchasesPerActiveDay,
        int maxPurchasesInOneDay,

        List<Integer> purchasesByHour,       // 24 values, Lagos time, index 0 = midnight
        List<Integer> purchasesByDayOfWeek,  // 7 values, index 0 = Monday
        Integer earliestPurchaseHour,        // Lagos time; null if no purchases
        Integer latestPurchaseHour,
        double nightShare,                   // share of purchases between 22:00 and 05:59

        List<CategoryStats> categories,
        List<MerchantStats> merchants
) {

    public record CategoryStats(
            String mcc,
            String label,
            int purchaseCount,
            BigDecimal totalSpend,
            double shareOfSpend,             // 0–100
            BigDecimal medianAmount,
            BigDecimal p90Amount,
            BigDecimal maxAmount,
            Instant lastPurchaseAt
    ) {
    }

    public record MerchantStats(
            String name,
            String mid,
            String mcc,
            String categoryLabel,
            int purchaseCount,
            BigDecimal totalSpend,
            BigDecimal medianAmount,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
    }
}