package carrental.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates a single chat request: normalizes language, builds the
 * effective system prompt (base + live DB context + language reminder),
 * sanitises the message list, then delegates the HTTP call to {@link
 * OpenRouterClient}.
 *
 * <p>Kept stateless on purpose — Spring Security holds session state, this
 * service does not.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final OpenRouterClient openRouter;
    private final LiveContextBuilder liveContext;

    public ChatService(OpenRouterClient openRouter, LiveContextBuilder liveContext) {
        this.openRouter = openRouter;
        this.liveContext = liveContext;
    }

    /**
     * Result of {@link #handle(Map)} — what the controller turns into HTTP.
     */
    public record Reply(Map<String, Object> body, String model, String language,
                        long latencyMs) { }

    /**
     * Mutates {@code payload} by adding/replacing the system message, then
     * dispatches to OpenRouter.
     */
    public Reply handle(Map<String, Object> payload, String language) {
        lang(payload, language);
        // Defensive copy of the (possibly immutable) client-supplied list.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> incoming = (List<Map<String, Object>>) payload.get("messages");
        List<Map<String, Object>> messages = (incoming == null) ? new ArrayList<>() : new ArrayList<>(incoming);
        payload.put("messages", messages);

        // Build effective system prompt.
        String systemPrompt = PromptTemplate.SYSTEM_PROMPT_BASE
                + "\n\n[LANGUAGE FOR THIS REQUEST: "
                + (language.equals("bn") ? "Bengali (বাংলা)" : "English")
                + " — keep ALL replies in this language unless the user explicitly switches.]"
                + "\n\n" + liveContext.build();

        // Smaller free-tier models are bad at reconciling competing
        // system prompts — drop anything the client tried to inject.
        messages.removeIf(m -> "system".equals(String.valueOf(m.get("role"))));
        Map<String, Object> systemMsg = new LinkedHashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(0, systemMsg);

        // Make sure every message has the two required OpenRouter fields.
        for (Map<String, Object> m : messages) {
            if (m.get("role") == null) m.put("role", "user");
            if (m.get("content") == null) m.put("content", "");
        }

        OpenRouterClient.Result r = openRouter.chat(payload);
        log.info("chat ok model={} language={} latency_ms={}", r.model(), language, r.latencyMs());
        return new Reply(r.body(), r.model(), language, r.latencyMs());
    }

    /** Ensures {@code payload["language"]} is canonical and present. */
    private void lang(Map<String, Object> payload, String language) {
        payload.put("language", language);
    }
}