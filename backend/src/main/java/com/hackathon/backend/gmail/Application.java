package com.hackathon.backend.gmail;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Application {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String company;
    private String role;
    private String status; // applied, assessment, interview, rejection, recruiter, other
    private Instant lastActivity;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL)
    @JsonManagedReference
    @OrderBy("date DESC")
    private List<EmailRecord> emails = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getLastActivity() { return lastActivity; }
    public void setLastActivity(Instant lastActivity) { this.lastActivity = lastActivity; }
    public List<EmailRecord> getEmails() { return emails; }
    public void setEmails(List<EmailRecord> emails) { this.emails = emails; }
}
