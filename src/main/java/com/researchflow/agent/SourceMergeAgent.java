package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class SourceMergeAgent {
    public String name() { return "source-merge-agent"; }

    public List<SourceDocument> execute(List<SourceDocument> external, List<SourceDocument> privateSources) {
        Map<String, SourceDocument> unique = new LinkedHashMap<>();
        List<SourceDocument> combined = new ArrayList<>(privateSources);
        combined.addAll(external);
        combined.forEach(source -> unique.putIfAbsent(key(source), source));
        return unique.values().stream().limit(10).toList();
    }

    private String key(SourceDocument source) {
        if (source.url() != null && !source.url().isBlank()) return source.url().toLowerCase(Locale.ROOT);
        return source.title().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
