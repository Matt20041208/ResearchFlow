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
                + "\n\n可用来源：\n" + sourceContext(input.sources())
                + "\n\n请输出简洁、谨慎的中文研究综述。每个事实性结论必须使用 [1] 形式引用来源，"
                + "保留不确定性，不得编造来源编号。";
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
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            SourceDocument source = sources.get(index);
            if (index > 0) result.append('\n');
            result.append(index + 1).append(". [").append(source.title()).append("](")
                    .append(source.url() == null ? "" : source.url()).append(")")
                    .append(" — ").append(source.sourceType())
                    .append("，置信度 ").append(String.format(java.util.Locale.ROOT, "%.2f", source.confidence()));
        }
        return result.isEmpty() ? "- 暂无来源" : result.toString();
    }

    private String sourceContext(List<SourceDocument> sources) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            SourceDocument source = sources.get(index);
            result.append('[').append(index + 1).append("] ").append(source.title())
                    .append("：").append(source.excerpt()).append('\n');
        }
        return result.toString();
    }
}
