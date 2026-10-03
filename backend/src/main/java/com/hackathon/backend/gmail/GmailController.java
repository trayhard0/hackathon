package com.hackathon.backend.gmail;

import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/gmail")
public class GmailController {

    private final GmailService gmailService;
    private final ClassifyClient classifyClient;

    public GmailController(GmailService gmailService, ClassifyClient classifyClient) {
        this.gmailService = gmailService;
        this.classifyClient = classifyClient;
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

}
