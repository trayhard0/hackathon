package com.hackathon.backend.gmail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final GmailService gmailService;
    private final ClassifyClient classifyClient;
    private final ApplicationRepository applicationRepository;
    private final EmailRecordRepository emailRecordRepository;

    public SyncService(GmailService gmailService,
                       ClassifyClient classifyClient,
                       ApplicationRepository applicationRepository,
                       EmailRecordRepository emailRecordRepository) {
        this.gmailService = gmailService;
        this.classifyClient = classifyClient;
        this.applicationRepository = applicationRepository;
        this.emailRecordRepository = emailRecordRepository;
    }

    public record SyncResult(int fetched, int added, int newApplications, int updatedApplications) {}

    public record CorrectResult(String messageId, String effectiveLabel, Long applicationId) {}

    private static final List<String> STAGE_ORDER =
            List.of("other", "recruiter", "applied", "assessment", "interview");

    private static final Set<String> VALID_LABELS =
            Set.of("applied", "assessment", "interview", "rejection", "recruiter", "other");

    public SyncResult sync(int max) {
        final List<EmailDto> emails;
        try {
            emails = gmailService.fetchRecentEmails(max);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gmail fetch failed: " + e.getMessage(), e);
        }

        int added = 0, newApps = 0, updatedApps = 0;
        List<Application> touched = new ArrayList<>();

        for (EmailDto dto : emails) {
            try {
                // Idempotency: skip anything we've already stored
                if (emailRecordRepository.existsById(dto.id())) {
                    continue;
                }

                ClassificationResult cr = classifyClient.classify(dto.from(), dto.subject(), dto.snippet());

                String label = cr.label();
                String company = extractCompany(dto.subject(), dto.from());
                String role = extractRole(dto.subject());
                String source = cr.source();
                String classificationText = dto.snippet();
                if (!"other".equals(label)) {
                    // Job-related: fetch full body so the LLM sees past the snippet
                    try {
                        classificationText = gmailService.fetchFullBody(dto.id());
                    } catch (Exception e) {
                        log.warn("Full body fetch failed for {}, falling back to snippet", dto.id());
                    }
                    ClassifyClient.UnderstandResult u = classifyClient.understand(dto.from(), dto.subject(), classificationText);
                    if ("ollama".equals(u.source())) {
                        label = u.phase();
                        if (!"Unknown".equals(u.company())) company = u.company();
                        if (!"Unknown".equals(u.role())) role = u.role();
                        source = "ollama";
                    } else if (!"other".equals(u.phase())) {
                        label = u.phase();  // Python rules fallback phase; keep regex company/role
                        source = u.source();
                    }
                }

                EmailRecord rec = new EmailRecord();
                rec.setGmailMessageId(dto.id());
                rec.setSender(dto.from());
                rec.setSubject(dto.subject());
                rec.setSnippet(classificationText);
                rec.setDate(parseDate(dto.date()));
                rec.setPredictedLabel(label);
                rec.setConfidence(cr.confidence());
                rec.setSource(source);


                if ("other".equals(label)) {
                    emailRecordRepository.save(rec); // standalone — never becomes a dashboard entry
                    added++;
                    continue;
                }

                String groupCompany = company;
                String groupRole = role;
                if (!"applied".equals(label)) {
                    // Follow-up email (assessment/interview/rejection/recruiter): attach to the
                    // existing application for this company, preferring the base "applied" row,
                    // instead of fragmenting on the LLM's inconsistent role extraction
                    var matches = applicationRepository.findByCompanyIgnoreCase(company);
                    var best = matches.stream()
                            .filter(a -> "applied".equals(a.getStatus()))
                            .findFirst()
                            .orElse(matches.stream().findFirst().orElse(null));
                    if (best != null) {
                        groupCompany = best.getCompany();
                        groupRole = best.getRole();
                    }
                }
                Optional<Application> existing =
                        applicationRepository.findByCompanyIgnoreCaseAndRoleIgnoreCase(groupCompany, groupRole);
                String finalCompany = groupCompany;
                String finalRole = groupRole;

                Application app = existing.orElseGet(() -> {
                    Application a = new Application();
                    a.setCompany(finalCompany);
                    a.setRole(finalRole);
                    a.setStatus(cr.label()); // placeholder; recomputed below
                    return a;
                });
                boolean isNew = app.getId() == null;

                rec.setApplication(app);
                app.getEmails().add(rec);

                if (rec.getDate() != null &&
                        (app.getLastActivity() == null || rec.getDate().isAfter(app.getLastActivity()))) {
                    app.setLastActivity(rec.getDate());
                }

                applicationRepository.save(app); // cascade persists the email
                touched.add(app);
                added++;
                if (isNew) newApps++; else updatedApps++;
            } catch (Exception e) {
                // One bad email must never kill the batch — skip it, log it, keep going
                log.warn("Skipping email {}: {}", dto.id(), e.getMessage());
            }
        }

        for (Application app : touched) {
            recomputeStatus(app);
            applicationRepository.save(app);
        }

        return new SyncResult(emails.size(), added, newApps, updatedApps);
    }

    public Map<String, Integer> applyLabelsToAll() throws Exception {
        Map<String, List<String>> byLabel = new HashMap<>();
        for (EmailRecord rec : emailRecordRepository.findAll()) {
            String label = rec.getCorrectedLabel() != null ? rec.getCorrectedLabel() : rec.getPredictedLabel();
            if (label == null || "other".equals(label)) continue;
            byLabel.computeIfAbsent(label.toLowerCase(), k -> new ArrayList<>())
                    .add(rec.getGmailMessageId());
        }
        return gmailService.applyLabels(byLabel);
    }

    /** Furthest stage reached wins; any rejection is terminal. */
    void recomputeStatus(Application app) {
        boolean rejected = false;
        int best = 0;
        for (EmailRecord e : app.getEmails()) {
            String label = e.getCorrectedLabel() != null ? e.getCorrectedLabel() : e.getPredictedLabel();
            if ("rejection".equals(label)) {
                rejected = true;
                break;
            }
            int rank = STAGE_ORDER.indexOf(label);
            if (rank > best) best = rank;
        }
        app.setStatus(rejected ? "rejection" : STAGE_ORDER.get(best));
    }

    @Transactional
    public CorrectResult correct(String gmailMessageId, String label) {
        if (!VALID_LABELS.contains(label)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown label: " + label);
        }
        EmailRecord rec = emailRecordRepository.findById(gmailMessageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown email: " + gmailMessageId));
        rec.setCorrectedLabel(label);

        Application app = rec.getApplication();
        if (app == null && !"other".equals(label)) {
            String company = extractCompany(rec.getSubject(), rec.getSender());
            String role = extractRole(rec.getSubject());
            app = applicationRepository.findByCompanyIgnoreCaseAndRoleIgnoreCase(company, role)
                    .orElseGet(() -> {
                        Application a = new Application();
                        a.setCompany(company);
                        a.setRole(role);
                        return a;
                    });
            rec.setApplication(app);
            app.getEmails().add(rec);
            applicationRepository.save(app);
        } else if (app != null && "other".equals(label)) {

            app.getEmails().remove(rec);
            rec.setApplication(null);
            if (app.getEmails().isEmpty()) {
                applicationRepository.delete(app);
                app = null;
            }
        }

        if (app != null) {
            recomputeStatus(app);
        }



        emailRecordRepository.save(rec);
        return new CorrectResult(gmailMessageId, label, app == null ? null : app.getId());
    }



    private static final List<Pattern> COMPANY_PATTERNS = List.of(
            Pattern.compile("applying to ([\\w&.'-]+(?: [\\w&.'-]+){0,3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("application to ([\\w&.'-]+(?: [\\w&.'-]+){0,3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("interest in ([\\w&.'-]+(?: [\\w&.'-]+){0,3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("update from ([\\w&.'-]+(?: [\\w&.'-]+){0,3})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("from ([\\w&.'-]+) has ", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern SUBJECT_PREFIX =
            Pattern.compile("^(.{2,30}?)\\s+[|–—-]\\s+");

    private static final Set<String> PLATFORM_DOMAINS = Set.of(
            "codesignal.com", "greenhouse-mail.io", "ashbyhq.com", "myworkday.com",
            "lever.co", "smartrecruiters.com", "icims.com", "taleo.net");

    private static final List<String> SUFFIXES = List.of(
            "talent team", "talent acquisition", "careers", "career", "recruiting",
            "hiring team", "hiring", "hr", "team", "jobs");

    String extractCompany(String subject, String sender) {
        String subj = subject == null ? "" : subject;

        for (Pattern p : COMPANY_PATTERNS) {
            Matcher m = p.matcher(subj);
            if (m.find()) {
                String cand = cleanCompany(m.group(1));
                if (cand != null) return cand;
            }
        }

        Matcher pre = SUBJECT_PREFIX.matcher(subj);
        if (pre.find()) {
            String cand = cleanCompany(pre.group(1));
            if (cand != null) return cand;
        }

        String fromSender = companyFromSender(sender);
        if (fromSender != null) return fromSender;

        return "Unknown";
    }

    /** Rejects req numbers ("R031695") and normalizes the capture. */
    private String cleanCompany(String raw) {
        String c = raw.trim().replaceAll("[.|,;:!]+$", "");
        if (c.isEmpty() || c.matches(".*\\d.*")) return null;
        if (c.equalsIgnoreCase("reminder") || c.equalsIgnoreCase("update")) return null;
        return c;
    }

    String companyFromSender(String sender) {
        if (sender == null) return null;
        String display = sender;
        String email = sender;
        int lt = sender.indexOf('<');
        if (lt >= 0) {
            display = sender.substring(0, lt).trim();
            int gt = sender.indexOf('>', lt);
            email = sender.substring(lt + 1, gt >= 0 ? gt : sender.length()).trim();
        }
        String domain = email.contains("@") ? email.substring(email.indexOf('@') + 1).toLowerCase() : "";
        if (PLATFORM_DOMAINS.contains(domain)) return null;

        String name = display.isEmpty() ? email : display;
        String lower = name.toLowerCase();
        for (String sfx : SUFFIXES) {
            if (lower.endsWith(" " + sfx)) {
                name = name.substring(0, name.length() - sfx.length()).trim();
                break;
            }
        }
        if (name.isEmpty() || name.contains("@")) return null;
        return name;
    }

    private static final List<String> ROLE_KEYWORDS = List.of(
            "software engineer", "software engineering", "swe", "data scientist", "data science",
            "frontend", "front end", "backend", "back end", "full stack", "systems engineer",
            "network engineer", "devops", "site reliability", "security engineer",
            "machine learning", "mobile engineer", "qa engineer", "product manager");

    private static final Pattern GENERIC_ROLE =
            Pattern.compile("([A-Z][\\w&/.-]+(?: [A-Z][\\w&/.-]+){0,4})\\s+(Intern|Co-?ops?)\\b");

    String extractRole(String subject) {
        String subj = subject == null ? "" : subject;
        String lower = subj.toLowerCase();
        for (String kw : ROLE_KEYWORDS) {
            if (lower.contains(kw)) {
                String title = toTitleCase(kw);
                if (lower.contains("intern")) return title + " Intern";
                if (lower.contains("co-op") || lower.contains("coop")) return title + " Co-op";
                return title;
            }
        }
        Matcher m = GENERIC_ROLE.matcher(subj);
        if (m.find()) {
            String level = m.group(2).toLowerCase().startsWith("co") ? "Co-op" : "Intern";
            return m.group(1).trim() + " " + level;
        }
        return "Unknown";
    }

    private String toTitleCase(String s) {
        String[] words = s.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    /** Gmail Date headers vary ("Sat, 3 Oct 2026 13:48:11 +0000", "... +0000 (UTC)"); be liberal. */
    private Instant parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().replaceAll("\\s*\\([^)]*\\)\\s*$", ""); // strip " (UTC)"
        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.RFC_1123_DATE_TIME,
                DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
                DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH));
        for (DateTimeFormatter f : fmts) {
            try {
                return ZonedDateTime.parse(s, f).toInstant();
            } catch (DateTimeParseException ignored) {
            }
        }
        log.warn("Unparseable date: {}", raw);
        return null;
    }

}
