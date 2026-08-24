package com.researchflow.agent.runtime;

import com.researchflow.agent.ComparisonAgent;
import com.researchflow.agent.EvidenceAgent;
import com.researchflow.agent.PlannerAgent;
import com.researchflow.agent.ResearchPlan;
import com.researchflow.agent.SourceSearchAgent;
import com.researchflow.agent.WriterAgent;
import com.researchflow.model.SourceDocument;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Component
public class AgentRegistry {
    private final Map<String, SubAgent> agents;

    public AgentRegistry(PlannerAgent planner, SourceSearchAgent search, EvidenceAgent evidence,
                         ComparisonAgent comparison, WriterAgent writer) {
        this.agents = Map.of(
                "planner-agent", adapter(planner.name(), Set.of("planning", "decomposition"), Set.of(),
                        context -> planner.execute(context.question())),
                "source-search-agent", adapter(search.name(), Set.of("paper-search", "retrieval"),
                        Set.of("crossref-search"),
                        context -> search.execute(context.get("plan", ResearchPlan.class))),
                "evidence-agent", adapter(evidence.name(), Set.of("evidence", "extraction"), Set.of(),
                        context -> evidence.execute(context.getList("sources", SourceDocument.class))),
                "comparison-agent", adapter(comparison.name(), Set.of("comparison", "analysis"), Set.of(),
                        context -> comparison.execute(new ComparisonAgent.Input(
                                context.get("plan", ResearchPlan.class), context.get("evidence", String.class)))),
                "writer-agent", adapter(writer.name(), Set.of("writing", "synthesis"), Set.of("llm-completion"),
                        context -> {
                            String comparisonText = context.get("comparison", String.class);
                            Object risk = context.get("risk");
                            if (risk != null) comparisonText += "\n\n" + risk;
                            return writer.execute(new WriterAgent.Input(context.question(), comparisonText,
                                    context.getList("sources", SourceDocument.class)));
                        }),
                "risk-agent", adapter("risk-agent", Set.of("risk-analysis"), Set.of(), context ->
                        "风险分析：基于当前证据，应重点核验数据偏差、评测可重复性、隐私合规和实际部署成本。"),
                "publisher-agent", adapter("publisher-agent", Set.of("publication"), Set.of("report-publish"),
                        context -> "报告已通过受控发布工具处理")
        );
    }

    public SubAgent get(String name) {
        SubAgent agent = agents.get(name);
        if (agent == null) throw new IllegalArgumentException("未注册的 Sub-Agent: " + name);
        return agent;
    }

    public List<String> names() { return agents.keySet().stream().sorted().toList(); }

    public List<SubAgent> all() {
        return agents.values().stream().sorted(java.util.Comparator.comparing(SubAgent::name)).toList();
    }

    private SubAgent adapter(String name, Set<String> capabilities, Set<String> requiredTools,
                             Function<AgentContext, Object> action) {
        return new SubAgent() {
            public String name() { return name; }
            public Set<String> capabilities() { return capabilities; }
            public Set<String> requiredTools() { return requiredTools; }
            public Object execute(AgentContext context) { return action.apply(context); }
        };
    }
}
