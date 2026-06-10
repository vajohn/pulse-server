package com.edge.pulse.services.translation;

import com.edge.pulse.configs.TranslationProperties;
import com.edge.pulse.data.enums.TranslationProvider;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MyMemory free translation API implementation of {@link TranslationService}.
 *
 * <p>API contract:
 * <pre>
 * GET https://api.mymemory.translated.net/get
 *   ?q=&lt;text, max 500 bytes UTF-8&gt;
 *   &amp;langpair=en|ar
 *   [&amp;de=email]   — optional; raises daily quota from 5k to 50k chars/day
 *   [&amp;key=apikey] — optional; private TM access
 * Response: { "responseData": { "translatedText": "..." }, "responseStatus": 200,
 *             "quotaFinished": false }
 * </pre>
 *
 * <p><strong>Why not RestClient?</strong> MyMemory requires a literal {@code |} in the
 * {@code langpair} query parameter (e.g. {@code en|ar}). Java's {@code URI} API rejects
 * raw {@code |} and percent-encodes it as {@code %7C}, which MyMemory does not accept.
 * {@code java.net.URL} is more lenient and passes the character through unmodified.
 * {@code HttpURLConnection} is therefore used directly for this service.
 *
 * <p><strong>500-byte limit:</strong> MyMemory rejects requests where {@code q} exceeds
 * 500 UTF-8 bytes. Long texts are automatically split on word boundaries, each chunk
 * is translated separately, and the results are joined with a space.
 *
 * <p><strong>Quota exhaustion:</strong> When {@code quotaFinished=true} the API indicates
 * the daily character budget is used up. A one-time WARN is logged and subsequent calls
 * still attempt translation (the API may still return machine-translated results).
 *
 * <p>On any failure the original text is returned — translation failure must never block
 * a save operation.
 *
 * <p><strong>Batch:</strong> MyMemory has no native batch endpoint. {@link #translateBatch}
 * loops individual {@link #translate} calls. Because {@link CachingTranslationService}
 * deduplicates cache hits before delegating, repeated phrases do not reach the network.
 */
@Slf4j
public class MyMemoryTranslationService implements TranslationService {

    static final String BASE_URL = "https://api.mymemory.translated.net";
    static final int MAX_BYTES = 500;

    private final TranslationProperties.MyMemory config;
    private final ObjectMapper objectMapper;
    private final AtomicBoolean quotaWarnedOnce = new AtomicBoolean(false);

    public MyMemoryTranslationService(TranslationProperties.MyMemory config,
                                      ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        // logMode() is called here rather than in @PostConstruct because this class is
        // instantiated with `new` inside a @Bean factory method (TranslationConfig).
        // Spring does not process @PostConstruct lifecycle callbacks on objects created
        // with `new` — only on beans it manages directly.
        logMode();
    }

    void logMode() {
        if (!config.getKey().isBlank()) {
            log.info("MyMemory translation: API key mode (private TM + higher quota)");
        } else if (!config.getEmail().isBlank()) {
            log.info("MyMemory translation: registered email mode (50k chars/day) — de={}",
                    config.getEmail());
        } else {
            log.info("MyMemory translation: anonymous mode (5k chars/day). "
                   + "Set MYMEMORY_EMAIL for 50k chars/day.");
        }
    }

    @Override
    public String translate(String text, String fromLocale, String toLocale) {
        List<String> chunks = splitIntoChunks(text);
        if (chunks.size() == 1) {
            return callApi(chunks.get(0), fromLocale, toLocale);
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) result.append(' ');
            result.append(callApi(chunks.get(i), fromLocale, toLocale));
        }
        return result.toString();
    }

    @Override
    public List<String> translateBatch(List<String> texts, String fromLocale, String toLocale) {
        return texts.stream()
                .map(text -> translate(text, fromLocale, toLocale))
                .toList();
    }

    @Override
    public boolean isAvailable() {
        return true; // anonymous usage requires no credentials
    }

    @Override
    public TranslationProvider getProvider() {
        return TranslationProvider.MYMEMORY;
    }

    // ── internal ──────────────────────────────────────────────────────────────

    /**
     * Makes one HTTP GET call to MyMemory and returns the translated text.
     * Package-private for test spy overrides. Falls back to the original text on any error.
     *
     * <p>Uses {@code java.net.URL} (not {@code URI}) so that the literal {@code |}
     * in {@code langpair=en|ar} is passed to the server unencoded. Java's {@code URI}
     * API would percent-encode it as {@code %7C}, which MyMemory rejects.
     */
    @SuppressWarnings("deprecation") // new URL(String) is the only way to send raw | without encoding
    String callApi(String text, String fromLocale, String toLocale) {
        try {
            StringBuilder queryStr = new StringBuilder();
            queryStr.append("q=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
            queryStr.append("&langpair=").append(fromLocale).append("|").append(toLocale);
            if (!config.getEmail().isBlank()) {
                queryStr.append("&de=").append(URLEncoder.encode(config.getEmail(), StandardCharsets.UTF_8));
            }
            if (!config.getKey().isBlank()) {
                queryStr.append("&key=").append(URLEncoder.encode(config.getKey(), StandardCharsets.UTF_8));
            }

            java.net.URL url = new java.net.URL(BASE_URL + "/get?" + queryStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(15_000);
            conn.setRequestProperty("Accept", "application/json");

            try {
                int status = conn.getResponseCode();
                try (InputStream is = status == HttpURLConnection.HTTP_OK
                        ? conn.getInputStream() : conn.getErrorStream()) {

                    if (is == null) {
                        log.warn("MyMemory returned empty response body (status {}) — returning original text", status);
                        return text;
                    }
                    MyMemoryResponse response = objectMapper.readValue(is, MyMemoryResponse.class);
                    return parseResponse(text, response);
                }
            } finally {
                conn.disconnect();
            }

        } catch (Exception e) {
            log.warn("MyMemory translation request failed — returning original text: {}", e.getMessage());
            return text;
        }
    }

    private String parseResponse(String originalText, MyMemoryResponse response) {
        if (response.quotaFinished() && quotaWarnedOnce.compareAndSet(false, true)) {
            log.warn("MyMemory daily character quota exceeded. "
                   + "Set MYMEMORY_EMAIL for 50k chars/day or reduce "
                   + "TRANSLATION_DAILY_CHAR_BUDGET to match your tier (anonymous=5000).");
        }

        if (response.responseStatus() != HttpURLConnection.HTTP_OK) {
            log.warn("MyMemory returned status {} ({}); returning original text",
                    response.responseStatus(), response.responseDetails());
            return originalText;
        }

        if (response.responseData() == null
                || response.responseData().translatedText() == null
                || response.responseData().translatedText().isBlank()) {
            log.warn("MyMemory response has null/blank translatedText; returning original");
            return originalText;
        }

        return response.responseData().translatedText();
    }

    /**
     * Splits text into chunks where each chunk is ≤ {@value #MAX_BYTES} UTF-8 bytes.
     * Splitting occurs on space boundaries to avoid mid-word cuts.
     */
    List<String> splitIntoChunks(String text) {
        if (text.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES) {
            return List.of(text);
        }
        log.debug("Text is {} bytes — splitting into ≤{}-byte chunks for MyMemory",
                text.getBytes(StandardCharsets.UTF_8).length, MAX_BYTES);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ", -1)) {
            if (current.isEmpty()) {
                if (word.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                    log.warn("Single word exceeds {} bytes — truncating for MyMemory", MAX_BYTES);
                    chunks.add(truncateToUtf8Bytes(word, MAX_BYTES));
                } else {
                    current.append(word);
                }
            } else {
                String candidate = current + " " + word;
                if (candidate.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
                    chunks.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current.append(' ').append(word);
                }
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * Truncates {@code s} to at most {@code maxBytes} UTF-8 bytes on a character boundary.
     */
    private static String truncateToUtf8Bytes(String s, int maxBytes) {
        int byteCount = 0;
        int i = 0;
        while (i < s.length()) {
            int codePoint = s.codePointAt(i);
            int cpBytes = new String(Character.toChars(codePoint))
                    .getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + cpBytes > maxBytes) break;
            byteCount += cpBytes;
            i += Character.charCount(codePoint);
        }
        return s.substring(0, i);
    }

    // ── response DTO ──────────────────────────────────────────────────────────

    /**
     * Package-private: accessible from tests in the same package.
     */
    record MyMemoryResponse(
            @JsonProperty("responseData")    ResponseData responseData,
            @JsonProperty("responseStatus")  int responseStatus,
            @JsonProperty("quotaFinished")   boolean quotaFinished,
            @JsonProperty("responseDetails") String responseDetails
    ) {
        record ResponseData(
                @JsonProperty("translatedText") String translatedText
        ) {}
    }
}
