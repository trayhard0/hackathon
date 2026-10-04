package com.hackathon.backend.gmail;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gmail")
public class GmailController {

    private final GmailService gmailService;
    private final ClassifyClient classifyClient;
    private final SyncService syncService;

    public GmailController(GmailService gmailService, ClassifyClient classifyClient, SyncService syncService) {
        this.gmailService = gmailService;
        this.classifyClient = classifyClient;
        this.syncService = syncService;
    }

    @GetMapping("/latest")
    public String latest() throws Exception {
        return gmailService.fetchLatestSubject();
    }

    @GetMapping("/recent")
    public List<EmailDto> recent(@RequestParam(defaultValue = "20") int max) throws Exception {
        return gmailService.fetchRecentEmails(max);
    }

    @PostMapping("/preview")
    public List<PreviewItem> preview(@RequestParam(defaultValue = "20") int max) throws Exception {
        List<PreviewItem> out = new ArrayList<>();
        for (EmailDto email : gmailService.fetchRecentEmails(max)) {
            ClassificationResult r = classifyClient.classify(email.from(), email.subject(), email.snippet());
            out.add(new PreviewItem(email.id(), email.from(), email.subject(),
                    email.date(), r.label(), r.confidence()));
        }
        return out;
    }

    @PostMapping("/apply")
    public ApplyResult apply(@RequestBody ApplyRequest request) {
        Map<String, List<String>> byLabel = new HashMap<>();
        List<String> failures = new ArrayList<>();
        for (ApplyItem item : request.items()) {
            if (item.messageId() == null || item.label() == null) {
                failures.add("missing messageId/label");
                continue;
            }
            byLabel.computeIfAbsent(item.label().toLowerCase(), k -> new ArrayList<>())
                    .add(item.messageId());
        }
        try {
            return new ApplyResult(gmailService.applyLabels(byLabel), failures);
        } catch (Exception e) {
            failures.add("gmail error: " + e.getMessage());
            return new ApplyResult(Map.of(), failures);
        }
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public String export(@RequestParam(defaultValue = "400") int max) throws Exception {
        StringBuilder sb = new StringBuilder("id,sender,subject,snippet,predicted_label,label\n");
        for (EmailDto email : gmailService.fetchRecentEmails(max)) {
            String predicted = classifyClient.classify(
                    email.from(), email.subject(), email.snippet()).label();
            sb.append(csv(email.id())).append(',')
                    .append(csv(email.from())).append(',')
                    .append(csv(email.subject())).append(',')
                    .append(csv(email.snippet())).append(',')
                    .append(csv(predicted)).append(',').append('\n');
        }
        return sb.toString();
    }

    private String csv(String s) {
        if (s == null) return "";
        return "\"" + s.replace("\"", "\"\"").replaceAll("[\\r\\n]+", " ") + "\"";
    }

    public record CorrectRequest(String messageId, String label) {}

    @PostMapping("/correct")
    public SyncService.CorrectResult correct(@RequestBody CorrectRequest req) {
        return syncService.correct(req.messageId(), req.label());
    }

}
