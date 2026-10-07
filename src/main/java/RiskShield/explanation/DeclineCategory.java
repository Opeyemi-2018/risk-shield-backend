package RiskShield.explanation;

public enum DeclineCategory {

    CARD_REPLACED("Card replaced", true),
    CARD_INACTIVE("Card not active", true),
    CARD_EXPIRED("Card expired", true),
    INSUFFICIENT_FUNDS("Not enough funds", true),
    SPENDING_LIMIT("Spending limit reached", true),
    INVALID_CARD_DETAILS("Card details didn't match", true),
    MERCHANT_NOT_ALLOWED("Merchant not allowed", true),
    SECURITY("Declined for your security", false),
    TECHNICAL("Temporary problem", true),
    UNKNOWN("Transaction declined", true);

    private final String headline;
    private final boolean detailsAllowed;

    DeclineCategory(String headline, boolean detailsAllowed) {
        this.headline = headline;
        this.detailsAllowed = detailsAllowed;
    }

    public String headline() {
        return headline;
    }

    public boolean detailsAllowed() {
        return detailsAllowed;
    }
}