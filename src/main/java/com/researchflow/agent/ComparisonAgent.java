package com.researchflow.agent;

import org.springframework.stereotype.Component;

@Component
public class ComparisonAgent implements ResearchAgent<ComparisonAgent.Input, String> {
    public record Input(ResearchPlan plan, String evidence) {}

    public String name() { return "comparison-agent"; }

    public String execute(Input input) {
        return "围绕“" + input.plan().focus() + "”比较现有证据：\n" + input.evidence()
                + "\n建议后续用真实论文元数据、实验指标和数据集补充定量比较。";
    }
}
