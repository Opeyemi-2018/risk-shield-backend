package RiskShield.service;

import RiskShield.dto.CreateUserRequest;
import RiskShield.dto.UserResponse;
import RiskShield.entity.User;
import RiskShield.entity.UserStatus;
import RiskShield.exception.EmailAlreadyExistsException;
import RiskShield.exception.UserNotFoundException;
import RiskShield.marqeta.MarqetaClient;
import RiskShield.marqeta.MarqetaCreateUserRequest;
import RiskShield.marqeta.MarqetaUserResponse;
import RiskShield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final MarqetaClient marqetaClient;

    public UserResponse registerUser(CreateUserRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }

        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phoneNumber(request.phoneNumber())
                .userStatus(UserStatus.PENDING)
                .build();
        user = userRepository.save(user);

        try {
            MarqetaUserResponse marqetaUser = marqetaClient.createUser(
                    new MarqetaCreateUserRequest(
                            user.getId().toString(),
                            user.getFirstName(),
                            user.getLastName(),
                            user.getEmail(),
                            user.getPhoneNumber()
                    )
            );
            user.setMarqetaUserToken(marqetaUser.token());
            user.setUserStatus(UserStatus.ACTIVE);
            log.info("User {} created on Marqeta", user.getId());
        } catch (RestClientException ex) {
            user.setUserStatus(UserStatus.FAILED);
            log.error("Failed to create user {} on Marqeta: {}", user.getId(), ex.getMessage());
        }

        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(UUID id) {
        return userRepository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(UserResponse::from)
                .toList();
    }
}