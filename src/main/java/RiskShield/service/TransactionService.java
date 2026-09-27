package RiskShield.service;

import RiskShield.dto.TransactionResponse;
import RiskShield.exception.UserNotFoundException;
import RiskShield.repository.TransactionRepository;
import RiskShield.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }
        return transactionRepository.findAllByUserIdOrderByTransactionTimeDesc(userId).stream()
                .map(TransactionResponse::from)
                .toList();
    }
}