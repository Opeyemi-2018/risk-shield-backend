package RiskShield.analysis;

import RiskShield.entity.Transaction;
import RiskShield.exception.UserNotFoundException;
import RiskShield.repository.TransactionRepository;
import RiskShield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpendingProfileService {

    public static final int DEFAULT_LOOKBACK_DAYS = 90;
    private static final int MIN_PURCHASES_FOR_PROFILE = 20;
    private static final int MIN_HISTORY_DAYS = 14;
    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /** Profile of the last {lookbackDays} days, up to now. */
    @Transactional(readOnly = true)
    public SpendingProfile buildProfile(UUID userId, int lookbackDays) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        Instant to = Instant.now();
        return buildProfile(userId, to.minus(lookbackDays, ChronoUnit.DAYS), to, lookbackDays);
    }

    /**
     * Profile from purchases in [from, to). Anomaly detection will call this with
     * to = the new transaction's time, so a purchase is never compared with itself
     * or with anything that happened after it.
     */
    @Transactional(readOnly = true)
    public SpendingProfile buildProfile(UUID userId, Instant from, Instant to, int lookbackDays) {
        List<Transaction> purchases = transactionRepository.findPurchases(userId, from, to);

        int count = purchases.size();
        List<BigDecimal> sortedAmounts = purchases.stream()
                .map(Transaction::getAmount)
                .sorted()
                .toList();
        BigDecimal total = sum(sortedAmounts);

        // How many days of history actually exist (not just the requested window).
        long historyDays = count == 0 ? 0
                : Math.max(1, Duration.between(purchases.get(0).getTransactionTime(), to).toDays());
        long spanForAverages = Math.min(lookbackDays, Math.max(historyDays, 1));

        // Purchases per calendar day (Lagos).
        Map<LocalDate, Long> perDay = purchases.stream()
                .collect(Collectors.groupingBy(t -> lagos(t).toLocalDate(), Collectors.counting()));

        // Hour of day and day of week (Lagos).
        int[] byHour = new int[24];
        int[] byDayOfWeek = new int[7];
        for (Transaction t : purchases) {
            ZonedDateTime local = lagos(t);
            byHour[local.getHour()]++;
            byDayOfWeek[local.getDayOfWeek().getValue() - 1]++;
        }
        int nightCount = 0;
        for (int hour = 0; hour < 24; hour++) {
            if (hour >= 22 || hour < 6) {
                nightCount += byHour[hour];
            }
        }

        Integer earliestHour = null;
        Integer latestHour = null;
        for (int hour = 0; hour < 24; hour++) {
            if (byHour[hour] > 0) {
                if (earliestHour == null) {
                    earliestHour = hour;
                }
                latestHour = hour;
            }
        }

        return new SpendingProfile(
                userId,
                from,
                to,
                lookbackDays,
                count >= MIN_PURCHASES_FOR_PROFILE && historyDays >= MIN_HISTORY_DAYS,
                count,
                money(total),
                count == 0 ? money(BigDecimal.ZERO) : money(total.divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP)),
                percentile(sortedAmounts, 0.5),
                money(total.multiply(BigDecimal.valueOf(30)).divide(BigDecimal.valueOf(spanForAverages), 4, RoundingMode.HALF_UP)),
                perDay.isEmpty() ? 0 : round1((double) count / perDay.size()),
                perDay.values().stream().mapToInt(Long::intValue).max().orElse(0),
                toList(byHour),
                toList(byDayOfWeek),
                earliestHour,
                latestHour,
                count == 0 ? 0 : round1(100.0 * nightCount / count) / 100.0,
                categoryStats(purchases, total),
                merchantStats(purchases)
        );
    }

    private List<SpendingProfile.CategoryStats> categoryStats(List<Transaction> purchases, BigDecimal total) {
        Map<String, List<Transaction>> byMcc = purchases.stream()
                .collect(Collectors.groupingBy(t -> Objects.toString(t.getMerchantMcc(), "unknown")));

        return byMcc.entrySet().stream()
                .map(entry -> {
                    List<Transaction> group = entry.getValue();
                    List<BigDecimal> amounts = group.stream().map(Transaction::getAmount).sorted().toList();
                    BigDecimal groupTotal = sum(amounts);
                    String mcc = "unknown".equals(entry.getKey()) ? null : entry.getKey();
                    return new SpendingProfile.CategoryStats(
                            mcc,
                            MerchantCategories.label(mcc),
                            group.size(),
                            money(groupTotal),
                            total.signum() == 0 ? 0
                                    : round1(groupTotal.multiply(BigDecimal.valueOf(100))
                                    .divide(total, 4, RoundingMode.HALF_UP).doubleValue()),
                            percentile(amounts, 0.5),
                            percentile(amounts, 0.9),
                            money(amounts.get(amounts.size() - 1)),
                            group.get(group.size() - 1).getTransactionTime()
                    );
                })
                .sorted(Comparator.comparing(SpendingProfile.CategoryStats::totalSpend).reversed())
                .toList();
    }

    private List<SpendingProfile.MerchantStats> merchantStats(List<Transaction> purchases) {
        Map<String, List<Transaction>> byMerchant = purchases.stream()
                .collect(Collectors.groupingBy(SpendingProfileService::merchantKey));

        return byMerchant.values().stream()
                .map(group -> {
                    Transaction latest = group.get(group.size() - 1);
                    List<BigDecimal> amounts = group.stream().map(Transaction::getAmount).sorted().toList();
                    return new SpendingProfile.MerchantStats(
                            latest.getMerchantName(),
                            latest.getMerchantMid(),
                            latest.getMerchantMcc(),
                            MerchantCategories.label(latest.getMerchantMcc()),
                            group.size(),
                            money(sum(amounts)),
                            percentile(amounts, 0.5),
                            group.get(0).getTransactionTime(),
                            latest.getTransactionTime()
                    );
                })
                .sorted(Comparator.comparingInt(SpendingProfile.MerchantStats::purchaseCount).reversed())
                .toList();
    }

    /**
     * Identifies a merchant. The MID (merchant ID) is more reliable than the name, because
     * names vary ("SHOPRITE LEKKI" vs "Shoprite Lekki"). Marqeta pads MIDs with leading
     * zeros, so they're stripped to compare consistently. Falls back to the lowercase name.
     */
    public static String merchantKey(Transaction t) {
        return merchantKey(t.getMerchantMid(), t.getMerchantName());
    }

    /**
     * Identifies a merchant. The MID is more reliable than the name, because names vary
     * ("SHOPRITE LEKKI" vs "Shoprite Lekki"). Marqeta pads MIDs with leading zeros,
     * so they're stripped to compare consistently. Falls back to the lowercase name.
     */
    public static String merchantKey(String mid, String name) {
        if (mid != null && !mid.isBlank()) {
            String trimmed = mid.replaceFirst("^0+", "");
            return "mid:" + (trimmed.isEmpty() ? "0" : trimmed);
        }
        return "name:" + Objects.toString(name, "unknown").trim().toLowerCase(Locale.ROOT);
    }

    /** Nearest-rank percentile on an already sorted list. */
    private static BigDecimal percentile(List<BigDecimal> sorted, double p) {
        if (sorted.isEmpty()) {
            return money(BigDecimal.ZERO);
        }
        int index = (int) Math.ceil(p * sorted.size()) - 1;
        index = Math.max(0, Math.min(index, sorted.size() - 1));
        return money(sorted.get(index));
    }

    private static BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static ZonedDateTime lagos(Transaction t) {
        return t.getTransactionTime().atZone(LAGOS);
    }

    private static List<Integer> toList(int[] values) {
        return Arrays.stream(values).boxed().toList();
    }


}