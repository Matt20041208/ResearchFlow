package com.researchflow.agent;

import com.researchflow.llm.SpringAiClient;
import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FactCheckerAgent implements ResearchAgent<FactCheckerAgent.Input, String> {
    private final SpringAiClient aiClient;

    public FactCheckerAgent(SpringAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public record Input(String question, String evidence, List<SourceDocument> sources, String instruction) {
        public Input(String question, String evidence, List<SourceDocument> sources) {
            this(question, evidence, sources, "核对证据与来源的一致性");
        }
    }

    public String name() { return "fact-checker-agent"; }

    public String execute(Input input) {
        String prompt = "研究问题：" + input.question() + "\n\n待核验证据：\n" + input.evidence()
                + "\n\n来源清单：\n" + sourceContext(input.sources())
                + "\n\n本轮修正要求：\n" + input.instruction()
                + "\n\n逐项判断证据是否有来源支撑，并标记无法核实、来源质量不足或引用错配的内容。";
        return aiClient.complete("你是事实核验 Agent。只能使用提供的来源，不得引入未给出的事实。", prompt)
                .orElseGet(() -> fallback(input.sources()));
    }

    private String sourceContext(List<SourceDocument> sources) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < sources.size(); index++) {
            SourceDocument source = sources.get(index);
            result.append('[').append(index + 1).append("] ").append(source.title())
                    .append(" | ").append(source.sourceType()).append(" | confidence=")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", source.confidence()))
                    .append(" | excerpt=").append(truncate(source.excerpt(), 500)).append('\n');
        }
        return result.toString();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private String fallback(List<SourceDocument> sources) {
        long weakSources = sources.stream().filter(source -> source.confidence() < 0.6).count();
        return "事实核验：共检查 " + sources.size() + " 个来源，其中 " + weakSources
                + " 个来源置信度低于 0.60；未由来源直接支持的结论应保留不确定性。";
    }
}
