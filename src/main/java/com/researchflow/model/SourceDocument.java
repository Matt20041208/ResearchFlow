package com.researchflow.model;

public record SourceDocument(String id, String sourceType, String title, String url,
                             String summary, String excerpt, double confidence) {
    public SourceDocument(String title, String url, String summary) {
        this(null, "EXTERNAL", title, url, summary, summary, 0.7);
    }
}
