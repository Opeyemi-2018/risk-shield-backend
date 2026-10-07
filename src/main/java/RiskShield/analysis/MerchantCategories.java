package RiskShield.analysis;

import java.util.Map;

public final class MerchantCategories {

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("5411", "Groceries"),
            Map.entry("5814", "Fast food"),
            Map.entry("5812", "Restaurants"),
            Map.entry("4121", "Taxi & rides"),
            Map.entry("5541", "Fuel"),
            Map.entry("5542", "Fuel"),
            Map.entry("5732", "Electronics"),
            Map.entry("4814", "Telecom & airtime"),
            Map.entry("4899", "Streaming & cable"),
            Map.entry("7011", "Hotels"),
            Map.entry("5999", "Other retail"),
            Map.entry("6011", "Cash withdrawal"),
            Map.entry("5944", "Jewelry"),
            Map.entry("7995", "Gambling")
    );

    private MerchantCategories() {
    }

    public static String label(String mcc) {
        if (mcc == null || mcc.isBlank()) {
            return "Unknown";
        }
        return LABELS.getOrDefault(mcc, "Other (MCC " + mcc + ")");
    }
}