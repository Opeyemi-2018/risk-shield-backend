package RiskShield.service;

import RiskShield.dto.CardResponse;
import RiskShield.entity.Card;
import RiskShield.entity.CardStatus;
import RiskShield.entity.User;
import RiskShield.entity.UserStatus;
import RiskShield.exception.MarqetaIntegrationException;
import RiskShield.exception.UserNotActiveException;
import RiskShield.exception.UserNotFoundException;
import RiskShield.marqeta.MarqetaCardResponse;
import RiskShield.marqeta.MarqetaClient;
import RiskShield.marqeta.MarqetaCreateCardRequest;
import RiskShield.marqeta.MarqetaProperties;
import RiskShield.repository.CardRepository;
import RiskShield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardService {

    private final UserRepository userRepository;
    private final CardRepository cardRepository;
    private final MarqetaClient marqetaClient;
    private final MarqetaProperties marqetaProperties;

    public CardResponse issueCard(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.getUserStatus() != UserStatus.ACTIVE || user.getMarqetaUserToken() == null) {
            throw new UserNotActiveException(userId);
        }

        MarqetaCardResponse marqetaCard;
        try {
            marqetaCard = marqetaClient.createCard(new MarqetaCreateCardRequest(
                    user.getMarqetaUserToken(),
                    marqetaProperties.cardProductToken()
            ));
        } catch (RestClientException ex) {
            log.error("Failed to create card on Marqeta for user {}: {}", userId, ex.getMessage());
            throw new MarqetaIntegrationException("Card could not be created with the card provider", ex);
        }

        Card card = Card.builder()
                .user(user)
                .marqetaCardToken(marqetaCard.token())
                .lastFour(marqetaCard.lastFour())
                .expiration(marqetaCard.expiration())
                .status(toCardStatus(marqetaCard.state()))
                .build();

        Card saved = cardRepository.save(card);
        log.info("Card {} issued to user {}", saved.getId(), userId);
        return CardResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getCards(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return cardRepository.findAllByUserId(userId).stream()
                .map(CardResponse::from)
                .toList();
    }

    private CardStatus toCardStatus(String marqetaState) {
        try {
            return CardStatus.valueOf(marqetaState);
        } catch (IllegalArgumentException | NullPointerException ex) {
            log.warn("Unknown Marqeta card state '{}', defaulting to UNSUPPORTED", marqetaState);
            return CardStatus.UNSUPPORTED;
        }
    }
}