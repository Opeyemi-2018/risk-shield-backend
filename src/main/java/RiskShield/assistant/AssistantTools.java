package RiskShield.assistant;

import RiskShield.analysis.InsightLevel;
import RiskShield.analysis.InsightService;
import RiskShield.analysis.MerchantCategories;
import RiskShield.dto.BalanceResponse;
import RiskShield.dto.TransactionInsightResponse;
import RiskShield.entity.Transaction;
import RiskShield.explanation.ExplanationFactBuilder;
import RiskShield.explanation.ExplanationFacts;
import RiskShield.repository.TransactionRepository;
import RiskShield.service.AccountService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Read-only tools the assistant may call. A NEW instance is created for every chat request,
 * bound to ONE user. No tool accepts a user ID, so the AI can never reach another user's data.
 * All numbers are computed here, in Java; the AI only reads them out.
 */
public class AssistantTools {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(LAGOS);
    private static final int DEFAULT_DAYS = 30;
    private static final int MAX_DAYS = 366;
    private static final int MAX_ITEMS = 20;

    private final UUID userId;
    private final TransactionRepository transactionRepository;
    private final InsightService insightService;
    private final ExplanationFactBuilder explanationFactBuilder;
    private final AccountService accountService;
    private final List<String> toolsUsed = new CopyOnWriteArrayList<>();

    public AssistantTools(UUID userId,
                          TransactionRepository transactionRepository,
                          InsightService insightService,
                          ExplanationFactBuilder explanationFactBuilder,
                          AccountService accountService) {
        this.userId = userId;
        this.transactionRepository = transactionRepository;
        this.insightService = insightService;
        this.explanationFactBuilder = explanationFactBuilder;
        this.accountService = accountService;
    }

    public List<String> toolsUsed() {
        return List.copyOf(new LinkedHashSet<>(toolsUsed));
    }

    // ---------- Results returned to the AI ----------

    public record CategoryTotal(String category, int purchases, BigDecimal total) {
    }

    public record SpendingSummary(String fromDate, String toDate, int purchases,
                                  BigDecimal totalSpent, String currency, List<CategoryTotal> byCategory) {
    }

    public record TransactionItem(String when, String merchant, String category,
                                  BigDecimal amount, String status) {
    }

    public record TransactionList(String fromDate, String toDate, int totalMatching,
                                  List<TransactionItem> newestFirst) {
    }

    public record CategoryChange(String category, BigDecimal firstMonthTotal,
                                 BigDecimal secondMonthTotal, BigDecimal difference) {
    }

    public record MonthComparison(String firstMonth, BigDecimal firstMonthTotal,
                                  String secondMonth, BigDecimal secondMonthTotal,
                                  BigDecimal difference, String percentChange,
                                  List<CategoryChange> biggestCategoryChanges) {
    }

    public record UnusualItem(String when, String merchant, BigDecimal amount,
                              String howUnusual, List<String> reasons) {
    }

    public record DeclinedItem(String when, String merchant, BigDecimal amount,
                               String reason, String nextStep) {
    }

    public record BalanceInfo(BigDecimal available, BigDecimal ledger, BigDecimal onHold,
                              String currency, String note) {
    }

    // ---------- Tools ----------

    @Tool(description = "Total amount the cardholder spent between two dates, with a breakdown by "
            + "category. Use for questions like 'how much did I spend this month', 'on food', 'last week'.")
    public SpendingSummary getSpendingSummary(
            @ToolParam(description = "Start date, inclusive, YYYY-MM-DD. Omit for 30 days ago.", required = false)
            String startDate,
            @ToolParam(description = "End date, inclusive, YYYY-MM-DD. Omit for today.", required = false)
            String endDate) {
        toolsUsed.add("spending summary");
        DateRange range = DateRange.of(startDate, endDate);
        List<Transaction> purchases = purchases(range);

        return new SpendingSummary(range.from().toString(), range.to().toString(),
                purchases.size(), money(sum(purchases)), "USD", categoryTotals(purchases));
    }

    @Tool(description = "List the cardholder's approved purchases between two dates, newest first "
            + "(at most 20). Optionally filter by category, e.g. 'Groceries', 'Fast food', 'Taxi'.")
    public TransactionList listTransactions(
            @ToolParam(description = "Start date, inclusive, YYYY-MM-DD. Omit for 30 days ago.", required = false)
            String startDate,
            @ToolParam(description = "End date, inclusive, YYYY-MM-DD. Omit for today.", required = false)
            String endDate,
            @ToolParam(description = "Optional category name to filter by.", required = false)
            String category) {
        toolsUsed.add("transactions");
        DateRange range = DateRange.of(startDate, endDate);

        List<Transaction> matching = purchases(range).stream()
                .filter(t -> category == null || category.isBlank()
                        || MerchantCategories.label(t.getMerchantMcc()).toLowerCase(Locale.ROOT)
                        .contains(category.trim().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(Transaction::getTransactionTime).reversed())
                .toList();

        List<TransactionItem> items = matching.stream()
                .limit(MAX_ITEMS)
                .map(t -> new TransactionItem(
                        DATE_TIME.format(t.getTransactionTime()),
                        t.getMerchantName(),
                        MerchantCategories.label(t.getMerchantMcc()),
                        money(t.getAmount()),
                        "PENDING".equals(t.getState()) ? "pending" : "completed"))
                .toList();

        return new TransactionList(range.from().toString(), range.to().toString(), matching.size(), items);
    }

    @Tool(description = "Compare total spending between two calendar months, with the categories "
            + "that changed most. Use for 'compare this month with last month'.")
    public MonthComparison compareMonths(
            @ToolParam(description = "First month, YYYY-MM, e.g. 2026-09") String firstMonth,
            @ToolParam(description = "Second month, YYYY-MM, e.g. 2026-10") String secondMonth) {
        toolsUsed.add("month comparison");
        YearMonth first = parseMonth(firstMonth, YearMonth.now(LAGOS).minusMonths(1));
        YearMonth second = parseMonth(secondMonth, YearMonth.now(LAGOS));

        List<Transaction> firstPurchases = purchases(DateRange.ofMonth(first));
        List<Transaction> secondPurchases = purchases(DateRange.ofMonth(second));

        BigDecimal firstTotal = money(sum(firstPurchases));
        BigDecimal secondTotal = money(sum(secondPurchases));
        BigDecimal difference = secondTotal.subtract(firstTotal);

        String percentChange = firstTotal.signum() == 0 ? "not available (no spending in the first month)"
                : difference.multiply(BigDecimal.valueOf(100))
                .divide(firstTotal, 1, RoundingMode.HALF_UP).toPlainString() + "%";

        Map<String, BigDecimal> firstByCategory = totalsByCategory(firstPurchases);
        Map<String, BigDecimal> secondByCategory = totalsByCategory(secondPurchases);
        Set<String> categories = new TreeSet<>(firstByCategory.keySet());
        categories.addAll(secondByCategory.keySet());

        List<CategoryChange> changes = categories.stream()
                .map(c -> {
                    BigDecimal a = firstByCategory.getOrDefault(c, BigDecimal.ZERO);
                    BigDecimal b = secondByCategory.getOrDefault(c, BigDecimal.ZERO);
                    return new CategoryChange(c, money(a), money(b), money(b.subtract(a)));
                })
                .sorted(Comparator.comparing((CategoryChange c) -> c.difference().abs()).reversed())
                .limit(3)
                .toList();

        return new MonthComparison(first.toString(), firstTotal, second.toString(), secondTotal,
                money(difference), percentChange, changes);
    }

    @Tool(description = "Purchases that looked unusual compared with the cardholder's normal habits, "
            + "between two dates, with the reasons. Use for 'anything unusual?' or 'suspicious purchases'.")
    public List<UnusualItem> getUnusualTransactions(
            @ToolParam(description = "Start date, inclusive, YYYY-MM-DD. Omit for 30 days ago.", required = false)
            String startDate,
            @ToolParam(description = "End date, inclusive, YYYY-MM-DD. Omit for today.", required = false)
            String endDate) {
        toolsUsed.add("unusual activity");
        DateRange range = DateRange.of(startDate, endDate);

        return insightService.getInsights(userId, InsightLevel.UNUSUAL).stream()
                .filter(i -> range.contains(i.transactionTime()))
                .limit(MAX_ITEMS)
                .map(i -> new UnusualItem(
                        DATE_TIME.format(i.transactionTime()),
                        i.merchantName(),
                        money(i.amount()),
                        i.level() == InsightLevel.HIGH ? "very unusual" : "a bit unusual",
                        i.signals().stream().map(s -> s.message()).toList()))
                .toList();
    }

    @Tool(description = "Declined purchases between two dates, each with the reason it was declined and "
            + "what to do next. Use for 'why was my card declined?'.")
    public List<DeclinedItem> getDeclinedTransactions(
            @ToolParam(description = "Start date, inclusive, YYYY-MM-DD. Omit for 30 days ago.", required = false)
            String startDate,
            @ToolParam(description = "End date, inclusive, YYYY-MM-DD. Omit for today.", required = false)
            String endDate) {
        toolsUsed.add("declined purchases");
        DateRange range = DateRange.of(startDate, endDate);

        return transactionRepository.findDeclined(userId, range.startInstant(), range.endInstant()).stream()
                .limit(10)
                .map(t -> {
                    ExplanationFacts facts = explanationFactBuilder.build(userId, t.getId());
                    return new DeclinedItem(
                            DATE_TIME.format(t.getTransactionTime()),
                            t.getMerchantName(),
                            money(t.getAmount()),
                            facts.reason(),
                            facts.nextStep());
                })
                .toList();
    }

    @Tool(description = "The cardholder's current balance: available to spend, ledger (settled), "
            + "and the amount on hold for pending purchases.")
    public BalanceInfo getBalance() {
        toolsUsed.add("balance");
        try {
            BalanceResponse balance = accountService.getBalance(userId);
            BigDecimal onHold = balance.ledgerBalance().subtract(balance.availableBalance());
            return new BalanceInfo(money(balance.availableBalance()), money(balance.ledgerBalance()),
                    money(onHold), balance.currency(), null);
        } catch (Exception ex) {
            return new BalanceInfo(null, null, null, "USD",
                    "The balance isn't available right now. Ask the cardholder to try again later.");
        }
    }

    // ---------- Helpers ----------

    private List<Transaction> purchases(DateRange range) {
        return transactionRepository.findPurchases(userId, range.startInstant(), range.endInstant());
    }

    private List<CategoryTotal> categoryTotals(List<Transaction> purchases) {
        return purchases.stream()
                .collect(Collectors.groupingBy(t -> MerchantCategories.label(t.getMerchantMcc())))
                .entrySet().stream()
                .map(e -> new CategoryTotal(e.getKey(), e.getValue().size(), money(sum(e.getValue()))))
                .sorted(Comparator.comparing(CategoryTotal::total).reversed())
                .toList();
    }

    private Map<String, BigDecimal> totalsByCategory(List<Transaction> purchases) {
        return purchases.stream().collect(Collectors.groupingBy(
                t -> MerchantCategories.label(t.getMerchantMcc()),
                Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));
    }

    private static BigDecimal sum(List<Transaction> transactions) {
        return transactions.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static YearMonth parseMonth(String value, YearMonth fallback) {
        try {
            return value == null || value.isBlank() ? fallback : YearMonth.parse(value.trim());
        } catch (Exception ex) {
            return fallback;
        }
    }

    /** A date range in Lagos time, end date inclusive. */
    private record DateRange(LocalDate from, LocalDate to) {

        static DateRange of(String start, String end) {
            LocalDate today = LocalDate.now(LAGOS);
            LocalDate to = parseDate(end, today);
            LocalDate from = parseDate(start, to.minusDays(DEFAULT_DAYS));
            if (from.isAfter(to)) {
                LocalDate swap = from;
                from = to;
                to = swap;
            }
            if (from.isBefore(to.minusDays(MAX_DAYS))) {
                from = to.minusDays(MAX_DAYS);
            }
            return new DateRange(from, to);
        }

        static DateRange ofMonth(YearMonth month) {
            return new DateRange(month.atDay(1), month.atEndOfMonth());
        }

        Instant startInstant() {
            return from.atStartOfDay(LAGOS).toInstant();
        }

        Instant endInstant() {
            return to.plusDays(1).atStartOfDay(LAGOS).toInstant();   // exclusive
        }

        boolean contains(Instant instant) {
            return !instant.isBefore(startInstant()) && instant.isBefore(endInstant());
        }

        private static LocalDate parseDate(String value, LocalDate fallback) {
            try {
                return value == null || value.isBlank() ? fallback : LocalDate.parse(value.trim());
            } catch (Exception ex) {
                return fallback;
            }
        }
    }
}