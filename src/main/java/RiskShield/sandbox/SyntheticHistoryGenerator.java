package RiskShield.sandbox;

import RiskShield.dto.SeedHistoryRequest;
import RiskShield.dto.SeedHistoryResponse;
import RiskShield.entity.Card;
import RiskShield.entity.CardStatus;
import RiskShield.entity.Transaction;
import RiskShield.entity.User;
import RiskShield.exception.UserNotFoundException;
import RiskShield.repository.CardRepository;
import RiskShield.repository.TransactionRepository;
import RiskShield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Generates a realistic, backdated spending history for one user (sandbox only).
 * Marqeta simulations always use the current time, so history must be generated locally.
 * Every generated row is marked synthetic = true.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "riskshield.sandbox.enabled", havingValue = "true")
public class SyntheticHistoryGenerator {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final String APPROVED_CODE = "0000";
    private static final String APPROVED_MEMO = "Approved or completed successfully";

    /** A merchant this "typical Lagos cardholder" uses, with their usual habits there. */
    private record MerchantPattern(
            String name, String mcc, String mid,
            double minAmount, double maxAmount,
            double timesPerWeek,
            int fromHour, int toHour,
            boolean weekdaysOnly
    ) {
    }

    private static final List<MerchantPattern> PATTERNS = List.of(
            new MerchantPattern("Shoprite Lekki",   "5411", "123456789", 15, 60, 1.5, 10, 20, false),
            new MerchantPattern("Spar Ikeja",       "5411", "223456789", 10, 45, 0.7, 10, 20, false),
            new MerchantPattern("Chicken Republic", "5814", "987654321",  5, 15, 2.0, 12, 21, false),
            new MerchantPattern("Domino's Pizza",   "5814", "887654321",  8, 20, 0.8, 18, 22, false),
            new MerchantPattern("Yellow Chilli",    "5812", "776655443", 20, 45, 0.4, 18, 22, false),
            new MerchantPattern("Bolt",             "4121", "554433221",  3, 12, 4.0,  7, 20, true),
            new MerchantPattern("Uber",             "4121", "554433222",  4, 14, 1.0,  7, 20, true),
            new MerchantPattern("TotalEnergies",    "5541", "665544332", 25, 50, 0.8,  8, 19, false),
            new MerchantPattern("MTN Airtime",      "4814", "443322110",  2, 10, 1.0,  8, 22, false)
    );

    /** A fixed monthly subscription, charged on the same day each month. */
    private static final MerchantPattern SUBSCRIPTION =
            new MerchantPattern("Netflix", "4899", "998877665", 7.99, 7.99, 0, 9, 10, false);
    private static final int SUBSCRIPTION_DAY_OF_MONTH = 5;

    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public SeedHistoryResponse generate(UUID userId, SeedHistoryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        long removed = request.replaceExistingOrDefault()
                ? transactionRepository.deleteByUserIdAndSyntheticTrue(userId)
                : 0;

        Card card = cardRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, CardStatus.ACTIVE)
                .orElse(null);

        int days = request.daysOrDefault();
        long seed = request.seed() != null ? request.seed() : userId.getMostSignificantBits();
        Random random = new Random(seed);

        LocalDate today = LocalDate.now(LAGOS);
        LocalDate from = today.minusDays(days);
        LocalDate to = today.minusDays(1);

        List<Transaction> rows = new ArrayList<>();
        int purchases = 0;
        int pending = 0;
        BigDecimal totalSpend = BigDecimal.ZERO;

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            boolean weekend = date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean recent = date.isAfter(today.minusDays(3));

            List<MerchantPattern> todays = new ArrayList<>();
            for (MerchantPattern pattern : PATTERNS) {
                if (pattern.weekdaysOnly() && weekend) {
                    continue;
                }
                double perDay = pattern.timesPerWeek() / (pattern.weekdaysOnly() ? 5.0 : 7.0);
                int count = (int) perDay + (random.nextDouble() < perDay - (int) perDay ? 1 : 0);
                for (int i = 0; i < count; i++) {
                    todays.add(pattern);
                }
            }
            if (date.getDayOfMonth() == SUBSCRIPTION_DAY_OF_MONTH) {
                todays.add(SUBSCRIPTION);
            }

            for (MerchantPattern pattern : todays) {
                BigDecimal amount = randomAmount(pattern, random);
                Instant time = randomTime(date, pattern, random);
                boolean stillPending = recent && random.nextDouble() < 0.3;

                String authToken = "seed-" + UUID.randomUUID();
                rows.add(purchaseRow(user, card, pattern, amount, time, authToken,
                        "authorization", stillPending ? "PENDING" : "COMPLETION", null));

                if (stillPending) {
                    pending++;
                } else {
                    rows.add(purchaseRow(user, card, pattern, amount, time, "seed-" + UUID.randomUUID(),
                            "authorization.clearing", "COMPLETION", authToken));
                }

                purchases++;
                totalSpend = totalSpend.add(amount);
            }
        }

        transactionRepository.saveAll(rows);
        log.info("Seeded {} synthetic purchases ({} rows) for user {} over {} days",
                purchases, rows.size(), userId, days);

        return new SeedHistoryResponse(userId, days, from, to, purchases, pending,
                rows.size(), removed, totalSpend);
    }

    private Transaction purchaseRow(User user, Card card, MerchantPattern pattern, BigDecimal amount,
                                    Instant time, String token, String type, String state,
                                    String precedingToken) {
        return Transaction.builder()
                .marqetaToken(token)
                .user(user)
                .card(card)
                .type(type)
                .state(state)
                .amount(amount)
                .currencyCode("USD")
                .responseCode(APPROVED_CODE)
                .responseMemo(APPROVED_MEMO)
                .merchantName(pattern.name())
                .merchantMid(pattern.mid())
                .merchantMcc(pattern.mcc())
                .merchantCity("Lagos")
                .transactionTime(time)
                .precedingTransactionToken(precedingToken)
                .synthetic(true)
                .build();
    }

    private BigDecimal randomAmount(MerchantPattern pattern, Random random) {
        double value = pattern.minAmount() + random.nextDouble() * (pattern.maxAmount() - pattern.minAmount());
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private Instant randomTime(LocalDate date, MerchantPattern pattern, Random random) {
        int hour = pattern.fromHour() + random.nextInt(Math.max(1, pattern.toHour() - pattern.fromHour()));
        int minute = random.nextInt(60);
        int second = random.nextInt(60);
        return date.atTime(hour, minute, second).atZone(LAGOS).toInstant();
    }
}