package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WriterAgent implements ResearchAgent<WriterAgent.Input, String> {
    public record Input(String question, String comparison, List<SourceDocument> sources) {}

    public String name() { return "writer-agent"; }

    public String execute(Input input) {
        String citations = input.sources().stream()
                .map(source -> "- [" + source.title() + "](" + source.url() + ")")
                .reduce((left, right) -> left + "\n" + right).orElse("- 暂无来源");
        return "# 研究报告\n\n## 研究问题\n" + input.question()
                + "\n\n## 综合分析\n" + input.comparison()
                + "\n\n## 来源\n" + citations;
    }
}
