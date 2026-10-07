package RiskShield.analysis;

import RiskShield.entity.Transaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Compares one purchase with the user's spending profile and returns a score and reasons.
 * Pure logic: no database access, so it's easy to unit test.
 */
@Component
public class AnomalyDetector {

    public static final int UNUSUAL_THRESHOLD = 25;
    public static final int HIGH_THRESHOLD = 50;

    private static final int MIN_CATEGORY_PURCHASES = 5;
    private static final int RAPID_PURCHASE_THRESHOLD = 3;
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    public record Assessment(int score, InsightLevel level, List<InsightSignal> signals) {
    }

    public Assessment assess(Transaction tx, SpendingProfile profile, int purchasesInPreviousHour) {
        if (!profile.sufficientHistory()) {
            return new Assessment(0, InsightLevel.NOT_ENOUGH_HISTORY, List.of());
        }

        List<InsightSignal> signals = new ArrayList<>();
        checkAmount(tx, profile, signals);
        checkCategoryAndMerchant(tx, profile, signals);
        checkTimeOfDay(tx, profile, signals);
        checkRapidPurchases(purchasesInPreviousHour, signals);
        checkShareOfMonthlySpend(tx, profile, signals);

        int score = Math.min(100, signals.stream().mapToInt(InsightSignal::points).sum());
        InsightLevel level = score >= HIGH_THRESHOLD ? InsightLevel.HIGH
                : score >= UNUSUAL_THRESHOLD ? InsightLevel.UNUSUAL
                : InsightLevel.NORMAL;

        return new Assessment(score, level, List.copyOf(signals));
    }

    private void checkAmount(Transaction tx, SpendingProfile profile, List<InsightSignal> signals) {
        BigDecimal amount = tx.getAmount();
        String label = MerchantCategories.label(tx.getMerchantMcc());

        Optional<SpendingProfile.CategoryStats> category = findCategory(tx, profile);

        if (category.isPresent() && category.get().purchaseCount() >= MIN_CATEGORY_PURCHASES) {
            BigDecimal median = category.get().medianAmount();
            BigDecimal p90 = category.get().p90Amount();
            BigDecimal threshold = p90.multiply(BigDecimal.valueOf(2)).max(median.multiply(BigDecimal.valueOf(3)));

            if (median.signum() > 0 && amount.compareTo(threshold) > 0) {
                double ratio = ratio(amount, median);
                int points = ratio >= 10 ? 40 : ratio >= 5 ? 30 : 20;
                signals.add(new InsightSignal(SignalCode.HIGH_AMOUNT_FOR_CATEGORY, points,
                        "%s is about %s× your typical %s purchase of %s."
                                .formatted(money(amount), times(ratio), label, money(median))));
            }
        } else {
            BigDecimal median = profile.medianPurchase();
            if (median.signum() > 0 && amount.compareTo(median.multiply(BigDecimal.valueOf(5))) >= 0) {
                double ratio = ratio(amount, median);
                int points = ratio >= 10 ? 30 : 20;
                signals.add(new InsightSignal(SignalCode.HIGH_AMOUNT_OVERALL, points,
                        "%s is about %s× your typical purchase of %s."
                                .formatted(money(amount), times(ratio), money(median))));
            }
        }
    }

    private void checkCategoryAndMerchant(Transaction tx, SpendingProfile profile, List<InsightSignal> signals) {
        if (findCategory(tx, profile).isEmpty()) {
            signals.add(new InsightSignal(SignalCode.NEW_CATEGORY, 20,
                    "This is your first %s purchase in the last %d days."
                            .formatted(MerchantCategories.label(tx.getMerchantMcc()), profile.lookbackDays())));
        }

        String key = SpendingProfileService.merchantKey(tx.getMerchantMid(), tx.getMerchantName());
        boolean merchantSeen = profile.merchants().stream()
                .anyMatch(m -> SpendingProfileService.merchantKey(m.mid(), m.name()).equals(key));
        if (!merchantSeen) {
            signals.add(new InsightSignal(SignalCode.NEW_MERCHANT, 10,
                    "This is your first purchase at %s."
                            .formatted(Objects.toString(tx.getMerchantName(), "this merchant"))));
        }
    }

    private void checkTimeOfDay(Transaction tx, SpendingProfile profile, List<InsightSignal> signals) {
        ZonedDateTime local = tx.getTransactionTime().atZone(LAGOS);
        int hour = local.getHour();
        List<Integer> byHour = profile.purchasesByHour();

        int nearby = byHour.get((hour + 23) % 24) + byHour.get(hour) + byHour.get((hour + 1) % 24);
        if (nearby > 0) {
            return;
        }

        boolean night = hour >= 22 || hour < 6;
        String usual = profile.earliestPurchaseHour() != null && profile.latestPurchaseHour() != null
                ? " (you usually spend between %02d:00 and %02d:59)"
                .formatted(profile.earliestPurchaseHour(), profile.latestPurchaseHour())
                : "";

        signals.add(new InsightSignal(SignalCode.UNUSUAL_TIME, night ? 25 : 15,
                "It was made at %02d:%02d, a time of day you don't normally spend%s."
                        .formatted(hour, local.getMinute(), usual)));
    }

    private void checkRapidPurchases(int purchasesInPreviousHour, List<InsightSignal> signals) {
        if (purchasesInPreviousHour >= RAPID_PURCHASE_THRESHOLD) {
            signals.add(new InsightSignal(SignalCode.RAPID_PURCHASES, 25,
                    "It was your %s purchase within an hour.".formatted(ordinal(purchasesInPreviousHour + 1))));
        }
    }

    private void checkShareOfMonthlySpend(Transaction tx, SpendingProfile profile, List<InsightSignal> signals) {
        BigDecimal monthly = profile.averageMonthlySpend();
        if (monthly.signum() <= 0) {
            return;
        }
        BigDecimal half = monthly.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (tx.getAmount().compareTo(half) >= 0) {
            int percent = (int) Math.round(ratio(tx.getAmount(), monthly) * 100);
            signals.add(new InsightSignal(SignalCode.LARGE_SHARE_OF_MONTHLY_SPEND, 20,
                    "This one purchase is %d%% of what you usually spend in a whole month (%s)."
                            .formatted(percent, money(monthly))));
        }
    }

    private Optional<SpendingProfile.CategoryStats> findCategory(Transaction tx, SpendingProfile profile) {
        return profile.categories().stream()
                .filter(c -> Objects.equals(c.mcc(), tx.getMerchantMcc()))
                .findFirst();
    }

    private static double ratio(BigDecimal value, BigDecimal base) {
        return value.divide(base, 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String times(double ratio) {
        return ratio >= 3 ? String.valueOf(Math.round(ratio)) : String.format("%.1f", ratio);
    }

    private static String money(BigDecimal amount) {
        return "$" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String ordinal(int n) {
        int mod100 = n % 100;
        String suffix = (mod100 >= 11 && mod100 <= 13) ? "th"
                : switch (n % 10) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }
}