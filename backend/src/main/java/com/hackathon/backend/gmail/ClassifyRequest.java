package com.hackathon.backend.gmail;

public record ClassifyRequest(String sender, String subject, String snippet) {}
