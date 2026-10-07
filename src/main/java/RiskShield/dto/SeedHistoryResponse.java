package RiskShield.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SeedHistoryResponse(
        UUID userId,
        int days,
        LocalDate from,
        LocalDate to,
        int purchasesCreated,
        int pendingPurchases,
        int transactionRowsCreated,
        long previousRowsRemoved,
        BigDecimal totalSpend
) {
}