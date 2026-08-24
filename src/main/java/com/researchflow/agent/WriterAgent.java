package com.researchflow.agent;

import com.researchflow.model.SourceDocument;
import com.researchflow.llm.SpringAiClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WriterAgent implements ResearchAgent<WriterAgent.Input, String> {
    private final SpringAiClient aiClient;

    public WriterAgent(SpringAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public record Input(String question, String comparison, List<SourceDocument> sources) {}

    public String name() { return "writer-agent"; }

    public String execute(Input input) {
        String prompt = "研究问题：" + input.question() + "\n\n证据与比较：\n" + input.comparison()
                + "\n\n请输出简洁、谨慎的中文研究综述，保留不确定性，不要编造来源。";
        var generated = aiClient.complete("你是科研写作 Agent，只能基于提供的证据写作。", prompt);
        if (generated.isPresent()) {
            return "# 研究报告\n\n## 研究问题\n" + input.question()
                    + "\n\n## 综合分析\n" + generated.get() + "\n\n## 来源\n" + citations(input.sources());
        }
        return fallbackReport(input);
    }

    private String fallbackReport(Input input) {
        return "# 研究报告\n\n## 研究问题\n" + input.question()
                + "\n\n## 综合分析\n" + input.comparison()
                + "\n\n## 来源\n" + citations(input.sources());
    }

    private String citations(List<SourceDocument> sources) {
        String citations = sources.stream()
                .map(source -> "- [" + source.title() + "](" + source.url() + ")")
                .reduce((left, right) -> left + "\n" + right).orElse("- 暂无来源");
        return citations;
    }
}
