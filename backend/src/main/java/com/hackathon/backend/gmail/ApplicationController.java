package com.hackathon.backend.gmail;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final SyncService syncService;
    private final ApplicationRepository applicationRepository;

    public ApplicationController(SyncService syncService, ApplicationRepository applicationRepository) {
        this.syncService = syncService;
        this.applicationRepository = applicationRepository;
    }

    public record ApplicationDto(Long id, String company, String role, String status,
                                 Instant lastActivity, int emailCount, String latestSubject) {}

    @PostMapping("/sync")
    public SyncService.SyncResult sync(@RequestParam(defaultValue = "50") int max) {
        return syncService.sync(max);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<ApplicationDto> list() {
        return applicationRepository.findAll().stream()
                .map(a -> new ApplicationDto(
                        a.getId(), a.getCompany(), a.getRole(), a.getStatus(),
                        a.getLastActivity(), a.getEmails().size(),
                        a.getEmails().isEmpty() ? null : a.getEmails().get(0).getSubject()))
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public Application detail(@PathVariable Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown application: " + id));
    }

    @GetMapping("/stats/funnel")
    @Transactional(readOnly = true)
    public Map<String, Long> funnel() {
        return applicationRepository.findAll().stream()
                .collect(Collectors.groupingBy(Application::getStatus, Collectors.counting()));
    }

    @PatchMapping("/{id}")
    @Transactional
    public ApplicationDto update(@PathVariable Long id, @RequestBody Map<String, String> fields) {
        var app = applicationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown application: " + id));
        if (fields.containsKey("company")) app.setCompany(fields.get("company"));
        if (fields.containsKey("role")) app.setRole(fields.get("role"));
        var saved = applicationRepository.save(app);
        return new ApplicationDto(
                saved.getId(), saved.getCompany(), saved.getRole(), saved.getStatus(),
                saved.getLastActivity(), saved.getEmails().size(),
                saved.getEmails().isEmpty() ? null : saved.getEmails().get(0).getSubject());
    }
}
