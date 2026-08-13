package carrental.chat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenRouterClientTest {

    @Test
    void normalizeLanguageDefaultsToEnglish() {
        assertEquals("en", OpenRouterClient.normalizeLanguage(null));
        assertEquals("en", OpenRouterClient.normalizeLanguage(""));
        assertEquals("en", OpenRouterClient.normalizeLanguage("english"));
        assertEquals("en", OpenRouterClient.normalizeLanguage("fr"));
    }

    @Test
    void normalizeLanguageAcceptsBengaliAliases() {
        assertEquals("bn", OpenRouterClient.normalizeLanguage("bn"));
        assertEquals("bn", OpenRouterClient.normalizeLanguage("BN"));
        assertEquals("bn", OpenRouterClient.normalizeLanguage("bangla"));
        assertEquals("bn", OpenRouterClient.normalizeLanguage("Bengali"));
        assertEquals("bn", OpenRouterClient.normalizeLanguage("বাংলা"));
    }

    @Test
    void stripHtmlRemovesAllTags() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> choice = new LinkedHashMap<>();
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "assistant");
        message.put("content", "<p>Hello <strong>world</strong></p><ul><li>x</li></ul>");
        choice.put("message", message);
        body.put("choices", List.of(choice));

        invokeStrip(body);

        @SuppressWarnings("unchecked")
        Map<String, Object> msgOut = (Map<String, Object>)
                ((Map<String, Object>) ((List<?>) body.get("choices")).get(0)).get("message");
        assertEquals("Hello worldx", msgOut.get("content"),
                "all HTML tags must be stripped");
    }

    /** stripHtmlFromResponse is private — reflectively invoke for testing. */
    private static void invokeStrip(Map<String, Object> body) {
        try {
            var m = OpenRouterClient.class.getDeclaredMethod("stripHtmlFromResponse", Map.class);
            m.setAccessible(true);
            m.invoke(null, body);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}