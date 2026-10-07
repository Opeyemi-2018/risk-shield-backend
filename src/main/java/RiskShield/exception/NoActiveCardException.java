package RiskShield.exception;

import java.util.UUID;

public class NoActiveCardException extends RuntimeException {
    public NoActiveCardException(UUID userId) {
        super("User " + userId + " has no active card. Issue a card first.");
    }
}