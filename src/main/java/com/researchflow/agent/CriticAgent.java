package com.researchflow.agent;

import com.researchflow.llm.SpringAiClient;
import org.springframework.stereotype.Component;

@Component
public class CriticAgent implements ResearchAgent<CriticAgent.Input, String> {
    private final SpringAiClient aiClient;

    public CriticAgent(SpringAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public record Input(String question, String comparison, String evidence, String instruction) {
        public Input(String question, String comparison, String evidence) {
            this(question, comparison, evidence, "检查结论的证据边界");
        }
    }

    public String name() { return "critic-agent"; }

    public String execute(Input input) {
        String prompt = "研究问题：" + input.question() + "\n\n候选分析：\n" + input.comparison()
                + "\n\n证据：\n" + input.evidence()
                + "\n\n本轮修正要求：\n" + input.instruction()
                + "\n\n指出证据缺口、逻辑跳跃、相互矛盾的结论和需要降低置信度的部分。";
        return aiClient.complete("你是批判性审查 Agent。只给出简洁、可执行的审查意见，不重写报告。", prompt)
                .orElse("批判性审查：现有结论应严格限定在已提供证据范围内；定量比较、样本代表性和外部有效性仍需补充验证。");
    }
}
