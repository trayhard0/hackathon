package com.hackathon.backend.gmail;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class EmailRecord {
    @Id
    private String gmailMessageId;

    private String threadId;
    private String sender;
    private String subject;
    @Column(length = 2000)
    private String snippet;
    private Instant date;
    private String predictedLabel;
    private Double confidence;
    private String source;
    private String correctedLabel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id")
    @JsonBackReference
    private Application application;

    public String getGmailMessageId() { return gmailMessageId; }
    public void setGmailMessageId(String gmailMessageId) { this.gmailMessageId = gmailMessageId; }
    public String getThreadId() { return threadId; }
    public void setThreadId(String threadId) { this.threadId = threadId; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
    public Instant getDate() { return date; }
    public void setDate(Instant date) { this.date = date; }
    public String getPredictedLabel() { return predictedLabel; }
    public void setPredictedLabel(String predictedLabel) { this.predictedLabel = predictedLabel; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCorrectedLabel() { return correctedLabel; }
    public void setCorrectedLabel(String correctedLabel) { this.correctedLabel = correctedLabel; }
    public Application getApplication() { return application; }
    public void setApplication(Application application) { this.application = application; }
}
