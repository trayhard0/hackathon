package com.hackathon.backend.gmail;
import java.util.List;
import java.util.Map;
public record ApplyResult(Map<String, Integer> applied, List<String> failures) {}
