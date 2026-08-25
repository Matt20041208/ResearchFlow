package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvidenceAgent implements ResearchAgent<List<SourceDocument>, String> {
    public String name() { return "evidence-agent"; }

    public String extract(SourceDocument source) {
        return source.title() + "：" + source.summary();
    }

    public String execute(List<SourceDocument> sources) {
        StringBuilder evidence = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            if (index > 0) evidence.append('\n');
            evidence.append('[').append(index + 1).append("] ").append(extract(sources.get(index)));
        }
        return evidence.isEmpty() ? "未提取到证据" : evidence.toString();
    }
}
