package RiskShield.exception;

import java.util.UUID;

public class UserNotActiveException extends RuntimeException {
    public UserNotActiveException(UUID userId) {
        super("User " + userId + " is not active on Marqeta");    }
}