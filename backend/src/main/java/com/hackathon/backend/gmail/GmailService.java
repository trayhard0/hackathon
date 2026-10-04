package com.hackathon.backend.gmail;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);

    private static final String APPLICATION_NAME = "Hackathon Gmail Organizer";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = Collections.singletonList(GmailScopes.GMAIL_MODIFY);

    private static final String CREDENTIALS_PATH =
            System.getProperty("user.home") + "/.hackathon/client_secret.json";
    private static final String TOKENS_DIR =
            System.getProperty("user.home") + "/.hackathon/tokens";

    private Gmail gmail;

    private Credential authorize() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
                new InputStreamReader(new FileInputStream(CREDENTIALS_PATH)));
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIR)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public Gmail getGmail() throws Exception {
        if (gmail == null) {
            NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            gmail = new Gmail.Builder(httpTransport, JSON_FACTORY, authorize())
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
        return gmail;
    }

    public String fetchLatestSubject() throws Exception {
        ListMessagesResponse response = getGmail().users().messages()
                .list("me").setMaxResults(1L).execute();
        List<Message> messages = response.getMessages();
        if (messages == null || messages.isEmpty()) return "No messages found.";
        Message full = getGmail().users().messages().get("me", messages.get(0).getId())
                .setFormat("metadata").setMetadataHeaders(List.of("Subject", "From")).execute();
        String subject = "(no subject)", from = "(unknown sender)";
        for (var header : full.getPayload().getHeaders()) {
            if (header.getName().equalsIgnoreCase("Subject")) subject = header.getValue();
            if (header.getName().equalsIgnoreCase("From")) from = header.getValue();
        }
        return "From: " + from + " | Subject: " + subject;
    }

    public List<EmailDto> fetchRecentEmails(int max) throws Exception {
        ListMessagesResponse response = getGmail().users().messages()
                .list("me").setMaxResults((long) max).execute();
        List<EmailDto> result = new ArrayList<>();
        if (response.getMessages() == null) return result;
        for (Message msg : response.getMessages()) {
            Message full = getGmail().users().messages().get("me", msg.getId())
                    .setFormat("metadata")
                    .setMetadataHeaders(List.of("Subject", "From", "Date"))
                    .execute();
            String subject = "(no subject)", from = "(unknown)", date = "";
            for (var header : full.getPayload().getHeaders()) {
                if (header.getName().equalsIgnoreCase("Subject")) subject = header.getValue();
                else if (header.getName().equalsIgnoreCase("From")) from = header.getValue();
                else if (header.getName().equalsIgnoreCase("Date")) date = header.getValue();
            }
            result.add(new EmailDto(msg.getId(), from, subject, full.getSnippet(), date));
        }
        return result;
    }

    /**
     * Fetches full body text for a single email.
     * Called only for job-related emails — keeps Gmail API quota usage low.
     */
    public String fetchFullBody(String messageId) throws Exception {
        Message full = getGmail().users().messages().get("me", messageId)
                .setFormat("full")
                .execute();
        return extractBodyText(full.getPayload());
    }

    /**
     * Extracts readable text from the MIME payload for classification.
     * Prefers text/plain; falls back to text/html with tags stripped.
     * Truncated to ~1500 chars — enough to see past the polite opening line.
     */
    private String extractBodyText(MessagePart payload) {
        StringBuilder sb = new StringBuilder();
        collectTextByMime(payload, sb, "text/plain");
        if (sb.length() == 0) {
            StringBuilder htmlSb = new StringBuilder();
            collectTextByMime(payload, htmlSb, "text/html");
            sb.append(htmlSb.toString().replaceAll("<[^>]+>", " "));
        }
        String text = sb.toString().replaceAll("\\s+", " ").trim();
        text = text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        return text.length() > 1500 ? text.substring(0, 1500) : text;
    }

    private void collectTextByMime(MessagePart part, StringBuilder sb, String targetMime) {
        if (part == null) return;
        if (targetMime.equalsIgnoreCase(part.getMimeType())
                && part.getBody() != null && part.getBody().getData() != null) {
            byte[] decoded = Base64.getUrlDecoder().decode(part.getBody().getData());
            sb.append(new String(decoded, StandardCharsets.UTF_8)).append(" ");
        }
        if (part.getParts() != null) {
            for (MessagePart p : part.getParts()) collectTextByMime(p, sb, targetMime);
        }
    }

    private static final Map<String, String> LABEL_NAMES = Map.of(
            "applied", "JobSearch/Applied",
            "assessment", "JobSearch/Assessment",
            "interview", "JobSearch/Interview",
            "rejection", "JobSearch/Rejection",
            "recruiter", "JobSearch/Recruiter"
    );

    public Map<String, Integer> applyLabels(Map<String, List<String>> messageIdsByLabel) throws Exception {
        Gmail gmail = getGmail();
        ListLabelsResponse listResp = gmail.users().labels().list("me").execute();
        Map<String, String> nameToId = new HashMap<>();
        for (Label l : listResp.getLabels()) nameToId.put(l.getName(), l.getId());

        Map<String, Integer> applied = new HashMap<>();
        for (var entry : messageIdsByLabel.entrySet()) {
            String gmailName = LABEL_NAMES.get(entry.getKey().toLowerCase());
            if (gmailName == null) continue; // "other" gets no label
            String labelId = nameToId.get(gmailName);
            if (labelId == null) {
                Label created = gmail.users().labels().create("me",
                        new Label().setName(gmailName)
                                .setLabelListVisibility("labelShow")
                                .setMessageListVisibility("show")).execute();
                labelId = created.getId();
                nameToId.put(gmailName, labelId);
            }
            gmail.users().messages().batchModify("me",
                    new BatchModifyMessagesRequest()
                            .setIds(entry.getValue())
                            .setAddLabelIds(List.of(labelId))).execute();
            applied.put(entry.getKey(), entry.getValue().size());
        }
        return applied;
    }
}
