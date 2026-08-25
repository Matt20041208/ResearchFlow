package com.researchflow.model;

public record Citation(int number, String sourceId, String sourceType, String title,
                       String url, String excerpt, double confidence) {
    public static Citation from(int number, SourceDocument source) {
        return new Citation(number, source.id(), source.sourceType(), source.title(), source.url(),
                source.excerpt(), source.confidence());
    }
}
