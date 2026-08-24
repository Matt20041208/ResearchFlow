package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EvidenceAgent implements ResearchAgent<List<SourceDocument>, String> {
    public String name() { return "evidence-agent"; }

    public String execute(List<SourceDocument> sources) {
        return sources.stream().map(source -> source.title() + "：" + source.summary())
                .reduce((left, right) -> left + "\n" + right).orElse("未提取到证据");
    }
}
