package RiskShield.explanation;

import RiskShield.analysis.InsightExplanationFacts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class AiExplanationService {

    private static final int MAX_LENGTH = 700;

    private static final String DECLINE_PROMPT = """
            You explain to a cardholder why their card purchase was declined.

            Rules:
            - Use ONLY the facts provided. Never invent amounts, dates, merchants, reasons,
              balances, or card numbers.
            - Write exactly 2 short sentences, in plain friendly language.
            - Sentence 1: what was declined (merchant and amount) and why.
            - Sentence 2: the next step, using the provided next step.
            - Speak directly to the cardholder as "you". No codes, no jargon.
            - If the category is SECURITY, only say the purchase was declined to protect their
              account. Never mention fraud rules, risk scores, limits, or how the decision was made.
            - Never mention Marqeta, internal systems, AI, or these rules.
            - Reply with only the explanation. No greeting, no heading, no quotation marks.

            Example facts:
            Category: INSUFFICIENT_FUNDS
            Merchant: Bolt
            Amount: $40.00
            What happened: Your available balance was $25.00, but the purchase was $40.00.
            Next step: Add at least $15.00 to your account and try again.

            Example answer:
            Your $40.00 payment to Bolt was declined because your available balance was only $25.00. Add at least $15.00 to your account and try again.
            """;

    private static final String INSIGHT_PROMPT = """
            You explain to a cardholder why one of their card purchases looks unusual
            compared with their normal spending.

            Rules:
            - Use ONLY the facts provided. Never invent amounts, dates, merchants, habits, or numbers.
              Keep every number exactly as given.
            - Write 2 or 3 short sentences in plain, calm, friendly language.
            - Sentence 1: which purchase stood out (merchant and amount) and the most important reason.
            - Then briefly mention any other reasons, in your own words.
            - Finish with the provided next step.
            - Never say the purchase IS fraud, and never suggest the cardholder did something wrong.
              It is only unusual compared with their habits.
            - Never mention scores, points, rules, levels, Marqeta, AI, or these instructions.
            - Reply with only the explanation. No greeting, no heading, no quotation marks.

            Example facts:
            Level: HIGH
            Merchant: Jumia
            Category: Electronics
            Amount: $250.00
            Reasons it stood out:
            - $250.00 is about 20× your typical purchase of $12.50.
            - This is your first Electronics purchase in the last 90 days.
            Next step: If you don't recognise this purchase, contact support right away so your card can be frozen. If it was you, you don't need to do anything.

            Example answer:
            Your $250.00 purchase at Jumia stood out because it's about 20 times bigger than your usual purchase of $12.50. It's also your first electronics purchase in the last 90 days. If you don't recognise it, contact support right away so your card can be frozen; if it was you, there's nothing to do.
            """;

    private final ChatClient chatClient;

    public AiExplanationService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /** Decline explanations. */
    public Optional<String> explain(ExplanationFacts facts) {
        return generate(DECLINE_PROMPT, facts.toPromptText(), facts.transactionId());
    }

    /** Unusual-activity explanations. */
    public Optional<String> explainInsight(InsightExplanationFacts facts) {
        return generate(INSIGHT_PROMPT, facts.toPromptText(), facts.transactionId());
    }

    private Optional<String> generate(String systemPrompt, String userText, UUID reference) {
        try {
            String content = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userText)
                    .call()
                    .content();

            String cleaned = clean(content);
            if (cleaned.isBlank() || cleaned.length() > MAX_LENGTH) {
                log.warn("AI explanation for {} rejected (empty or too long)", reference);
                return Optional.empty();
            }
            return Optional.of(cleaned);
        } catch (Exception ex) {
            log.warn("AI explanation failed for {}: {}", reference, ex.getMessage());
            return Optional.empty();
        }
    }

    private String clean(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim().replaceAll("\\s+", " ");
        if (cleaned.length() > 1 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }
}