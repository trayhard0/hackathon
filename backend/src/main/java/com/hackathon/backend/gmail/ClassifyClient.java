package com.hackathon.backend.gmail;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class ClassifyClient {

    private static final String CLASSIFY_URL = "http://localhost:8000/classify";
    private final RestTemplate rest = new RestTemplate();

    public ClassificationResult classify(String sender, String subject, String snippet) {
        try {
            ClassificationResult res = rest.postForObject(
                    CLASSIFY_URL, new ClassifyRequest(sender, subject, snippet),
                    ClassificationResult.class);
            return res != null ? res : new ClassificationResult("other", 0.0);
        } catch (Exception e) {
            // FastAPI down? Degrade gracefully instead of crashing the request.
            return new ClassificationResult("other", 0.0);
        }
    }
}
