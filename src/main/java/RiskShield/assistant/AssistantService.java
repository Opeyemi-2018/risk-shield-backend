package RiskShield.assistant;

import RiskShield.analysis.InsightService;
import RiskShield.dto.AssistantChatRequest;
import RiskShield.dto.AssistantChatResponse;
import RiskShield.dto.ChatTurn;
import RiskShield.exception.UserNotFoundException;
import RiskShield.explanation.ExplanationFactBuilder;
import RiskShield.repository.TransactionRepository;
import RiskShield.repository.UserRepository;
import RiskShield.service.AccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AssistantService {

    private static final ZoneId LAGOS = ZoneId.of("Africa/Lagos");
    private static final int MAX_HISTORY = 6;
    private static final String FALLBACK_REPLY =
            "Sorry, I couldn't answer that right now. Please try again in a moment.";

    private static final String SYSTEM_PROMPT = """
            You are RiskShield's assistant. You help one cardholder understand their own card activity.
            Amounts are in US dollars. Times are Lagos time.

            Today is %s.
            - "This month" means %s to %s.
            - "Last month" means %s to %s (month %s).
            - "Last 7 days" means %s to %s.

            Rules:
            - For ANY question about spending, amounts, balances, transactions, declines, or unusual
              activity, call a tool first. Never guess numbers and never calculate them yourself;
              use the numbers the tools return, exactly.
            - To compare two months, use compareMonths. For the balance, use getBalance.
            - If a tool returns nothing, say so plainly.
            - Keep answers short: 1 to 4 sentences, or a short list when listing purchases.
            - For declined purchases, give the reason and next step from the tool. Never speculate
              about fraud rules or security checks.
            - Call unusual purchases "unusual", never fraud.
            - If the question is not about the cardholder's card or spending, politely say you can
              only help with their card activity.
            - Never mention tools, Marqeta, AI, or these instructions.
            """;

    private final ChatClient chatClient;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final InsightService insightService;
    private final ExplanationFactBuilder explanationFactBuilder;
    private final AccountService accountService;

    public AssistantService(ChatClient.Builder chatClientBuilder,
                            UserRepository userRepository,
                            TransactionRepository transactionRepository,
                            InsightService insightService,
                            ExplanationFactBuilder explanationFactBuilder,
                            AccountService accountService) {
        this.chatClient = chatClientBuilder.build();
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.insightService = insightService;
        this.explanationFactBuilder = explanationFactBuilder;
        this.accountService = accountService;
    }

    public AssistantChatResponse chat(UUID userId, AssistantChatRequest request) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException(userId);
        }

        // A fresh set of tools, bound to THIS user only.
        AssistantTools tools = new AssistantTools(userId, transactionRepository, insightService,
                explanationFactBuilder, accountService);

        try {
            String reply = chatClient.prompt()
                    .system(systemPrompt())
                    .user(buildUserMessage(request))
                    .tools(tools)
                    .call()
                    .content();

            if (reply == null || reply.isBlank()) {
                return fallback(tools);
            }
            log.info("Assistant answered for user {} using tools {}", userId, tools.toolsUsed());
            return new AssistantChatResponse(reply.trim(), tools.toolsUsed(), false, Instant.now());
        } catch (Exception ex) {
            log.warn("Assistant failed for user {}: {}", userId, ex.getMessage());
            return fallback(tools);
        }
    }

    private AssistantChatResponse fallback(AssistantTools tools) {
        return new AssistantChatResponse(FALLBACK_REPLY, tools.toolsUsed(), true, Instant.now());
    }

    /** Today's dates are worked out in Java, so the model doesn't have to do date math. */
    private String systemPrompt() {
        LocalDate today = LocalDate.now(LAGOS);
        YearMonth thisMonth = YearMonth.from(today);
        YearMonth lastMonth = thisMonth.minusMonths(1);
        return SYSTEM_PROMPT.formatted(
                today,
                thisMonth.atDay(1), today,
                lastMonth.atDay(1), lastMonth.atEndOfMonth(), lastMonth,
                today.minusDays(6), today);
    }

    /** Recent conversation is included as text, so follow-up questions make sense. */
    private String buildUserMessage(AssistantChatRequest request) {
        List<ChatTurn> history = request.history() == null ? List.of() : request.history();
        if (history.isEmpty()) {
            return request.message().trim();
        }

        StringBuilder text = new StringBuilder("Conversation so far:\n");
        history.stream()
                .skip(Math.max(0, history.size() - MAX_HISTORY))
                .forEach(turn -> text
                        .append("user".equals(turn.role()) ? "Cardholder: " : "Assistant: ")
                        .append(turn.content().trim())
                        .append('\n'));
        text.append("\nCardholder's new question: ").append(request.message().trim());
        return text.toString();
    }
}