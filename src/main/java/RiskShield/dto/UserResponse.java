package RiskShield.dto;

import RiskShield.entity.User;
import RiskShield.entity.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        String phoneNumber,
        UserStatus status,
        LocalDateTime createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getUserStatus(),
                user.getCreatedAt()
        );
    }
}