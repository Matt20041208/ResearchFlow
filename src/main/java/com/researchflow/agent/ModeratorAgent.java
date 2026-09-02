package com.researchflow.agent;

import com.researchflow.llm.SpringAiClient;
import org.springframework.stereotype.Component;

@Component
public class ModeratorAgent implements ResearchAgent<ModeratorAgent.Input, String> {
    private final SpringAiClient aiClient;

    public ModeratorAgent(SpringAiClient aiClient) {
        this.aiClient = aiClient;
    }

    public record Input(String question, String comparison, String criticism, String factCheck,
                        String risk, String instruction) {
        public Input(String question, String comparison, String criticism, String factCheck, String risk) {
            this(question, comparison, criticism, factCheck, risk, "按证据质量主持讨论并形成共识");
        }
    }

    public String name() { return "moderator-agent"; }

    public String execute(Input input) {
        String prompt = "研究问题：" + input.question()
                + "\n\n分析 Agent：\n" + input.comparison()
                + "\n\n批判 Agent：\n" + input.criticism()
                + "\n\n事实核验 Agent：\n" + input.factCheck()
                + (input.risk() == null ? "" : "\n\n风险 Agent：\n" + input.risk())
                + "\n\n本轮修正要求：\n" + input.instruction()
                + "\n\n请主持收敛：区分已达成共识、仍有分歧、证据不足和最终写作约束。";
        return aiClient.complete("你是多角色讨论的 Moderator。不要多数表决，应按证据质量形成可审计共识。", prompt)
                .orElseGet(() -> fallback(input));
    }

    private String fallback(Input input) {
        return "讨论共识：\n" + input.comparison()
                + "\n\n审查约束：\n" + input.criticism()
                + "\n\n核验约束：\n" + input.factCheck()
                + (input.risk() == null ? "" : "\n\n风险约束：\n" + input.risk());
    }
}
