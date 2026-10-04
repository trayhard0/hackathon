package com.hackathon.backend.gmail;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class ClassifyClient {

    private static final String CLASSIFY_URL = "http://localhost:8000/classify";
    private final RestTemplate rest = new RestTemplate();

    public ClassificationResult classify(String sender, String subject, String snippet) {
        try {
            ClassificationResult res = rest.postForObject(
                    CLASSIFY_URL, new ClassifyRequest(sender, subject, snippet),
                    ClassificationResult.class);
            return res != null ? res : new ClassificationResult("other", 0.0, "rules");
        } catch (Exception e) {
            // FastAPI down? Degrade gracefully instead of crashing the request.
            return new ClassificationResult("other", 0.0, "rules");
        }
    }

    public record UnderstandResult(String phase, String company, String role, String source) {}

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public UnderstandResult understand(String sender, String subject, String snippet) {
        try {
            String body = MAPPER.writeValueAsString(
                    Map.of("sender", sender, "subject", subject, "snippet", snippet));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8000/understand"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            String resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body();
            JsonNode n = MAPPER.readTree(resp);
            return new UnderstandResult(
                    n.path("phase").asText("other"),
                    n.path("company").asText("Unknown"),
                    n.path("role").asText("Unknown"),
                    n.path("source").asText("ollama"));
        } catch (Exception e) {
            return new UnderstandResult("other", "Unknown", "Unknown", "rules-fallback");
        }
    }

}
