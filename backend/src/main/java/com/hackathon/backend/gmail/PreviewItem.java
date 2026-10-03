package com.hackathon.backend.gmail;

public record PreviewItem(String id, String from, String subject, String date, String predictedLabel, double confidence) {}
