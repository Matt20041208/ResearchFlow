package com.researchflow.agent;

import org.springframework.stereotype.Component;

@Component
public class PlannerAgent implements ResearchAgent<String, ResearchPlan> {
    public String name() { return "planner-agent"; }

    public ResearchPlan execute(String question) {
        return new ResearchPlan(question, question.replace(' ', '+'), "方法、证据与实际应用");
    }
}
