package RiskShield.entity;

public enum CardStatus {
    UNACTIVATED, ACTIVE, SUSPENDED, TERMINATED, LIMITED, UNSUPPORTED;

    public static CardStatus fromMarqeta(String state) {
        if (state == null) {
            return UNSUPPORTED;
        }
        try {
            return valueOf(state);
        } catch (IllegalArgumentException ex) {
            return UNSUPPORTED;
        }
    }
}