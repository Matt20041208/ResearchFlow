package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

@Component
public class SourceSearchAgent implements ResearchAgent<ResearchPlan, List<SourceDocument>> {
    private final ObjectMapper objectMapper;
    private final String crossrefUrl;
    private final int rows;
    private final HttpClient httpClient;

    public SourceSearchAgent(ObjectMapper objectMapper,
                             @Value("${research-flow.search.crossref-url}") String crossrefUrl,
                             @Value("${research-flow.search.rows:5}") int rows) {
        this.objectMapper = objectMapper;
        this.crossrefUrl = crossrefUrl;
        this.rows = rows;
        this.httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    }

    public String name() { return "source-search-agent"; }

    public List<SourceDocument> execute(ResearchPlan plan) {
        try {
            String query = URLEncoder.encode(plan.searchQuery().replace('+', ' '), StandardCharsets.UTF_8);
            URI uri = URI.create(crossrefUrl + "?query.bibliographic=" + query + "&rows=" + rows);
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(java.time.Duration.ofSeconds(20))
                    .header("Accept", "application/json")
                    .header("User-Agent", "ResearchFlow/0.1 (mailto:researchflow@example.com)")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<SourceDocument> sources = parse(response.body());
                if (!sources.isEmpty()) return sources;
            }
        } catch (Exception ignored) {
            // External search is optional; the local fallback keeps the workflow usable offline.
        }
        return fallback(plan);
    }

    private List<SourceDocument> parse(String body) throws Exception {
        JsonNode items = objectMapper.readTree(body).path("message").path("items");
        List<SourceDocument> sources = new ArrayList<>();
        for (JsonNode item : items) {
            String title = item.path("title").path(0).asText("Untitled paper");
            String url = item.path("URL").asText("");
            String summary = item.path("abstract").asText("No abstract available")
                    .replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").trim();
            sources.add(new SourceDocument(title, url, summary));
        }
        return sources;
    }

    private List<SourceDocument> fallback(ResearchPlan plan) {
        return List.of(new SourceDocument("Offline search placeholder", "https://example.org/search?q=" + plan.searchQuery(),
                "Crossref 不可用，当前使用离线占位来源。"));
    }
}
